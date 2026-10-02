package zw.co.zimfete.assetfinance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import zw.co.zimfete.assetfinance.fineract.FineractClient;
import zw.co.zimfete.assetfinance.fineract.FineractException;

/** In-memory stand-in for Fineract that records every posting so tests can check nothing is doubled. */
public class FakeFineractClient implements FineractClient {

    public final Map<Long, ClientInfo> clients = new HashMap<>();
    public final Map<Long, Savings> savings = new HashMap<>();
    public final Map<Long, Loan> loans = new HashMap<>();
    public final List<String> postings = new ArrayList<>();
    private final Set<String> failOnce = new HashSet<>();
    private final AtomicLong ids = new AtomicLong(1000);

    /** Charge definition id of the opening fee, matching zimfete.fineract.opening-fee-charge-id in tests. */
    public static final long OPENING_FEE_CHARGE = 77;
    public static final BigDecimal OPENING_FEE = new BigDecimal("10.00");

    public static final class Savings {
        public long clientId;
        public String externalId;
        public BigDecimal balance = BigDecimal.ZERO;
        public BigDecimal feeOutstanding = OPENING_FEE;
        public final List<SavingsTransaction> transactions = new ArrayList<>();
    }

    public static final class Loan {
        public long clientId;
        public String externalId;
        public BigDecimal principal;
        public String status = "pending";
    }

    public void addClient(long id, String name, long officeId) {
        clients.put(id, new ClientInfo(id, name, officeId, null, true));
    }

    public void deposit(long savingsId, String amount, LocalDate date) {
        Savings s = savings.get(savingsId);
        BigDecimal value = new BigDecimal(amount);
        s.balance = s.balance.add(value);
        s.transactions.add(new SavingsTransaction(ids.incrementAndGet(), date, value, true, false, false));
    }

    public void closeLoan(long loanId) {
        loans.get(loanId).status = "closed";
    }

    /** Makes the next call of the named operation fail, as if Fineract were unreachable. */
    public void failOnce(String operation) {
        failOnce.add(operation);
    }

    private void maybeFail(String operation) {
        if (failOnce.remove(operation)) {
            throw new FineractException("Simulated failure in " + operation);
        }
    }

    @Override
    public ClientInfo getClient(long clientId) {
        ClientInfo c = clients.get(clientId);
        if (c == null) {
            throw new FineractException("Client " + clientId + " not found");
        }
        return c;
    }

    @Override
    public List<Office> listOffices() {
        return List.of(new Office(1, "Head Office"), new Office(2, "Marondera"), new Office(4, "Hwedza"));
    }

    @Override
    public List<ClientSummary> searchClients(String query) {
        return clients.values().stream()
                .filter(c -> c.displayName().toLowerCase().contains(query.toLowerCase()))
                .map(c -> new ClientSummary(c.id(), c.displayName(), "000" + c.id(), c.officeId(), "Office " + c.officeId(),
                        null, c.active()))
                .toList();
    }

    @Override
    public long openAssetDepositAccount(long clientId, String externalId, LocalDate date) {
        long id = ids.incrementAndGet();
        Savings s = new Savings();
        s.clientId = clientId;
        s.externalId = externalId;
        savings.put(id, s);
        postings.add("open-savings:" + id);
        return id;
    }

    @Override
    public SavingsAccountInfo getSavingsAccount(long savingsAccountId) {
        Savings s = savings.get(savingsAccountId);
        return new SavingsAccountInfo(savingsAccountId, s.clientId, "USD", s.balance, List.copyOf(s.transactions),
                List.of(new AccountCharge(9000 + savingsAccountId, OPENING_FEE_CHARGE, s.feeOutstanding)));
    }

    @Override
    public void paySavingsCharge(long savingsAccountId, long accountChargeId, BigDecimal amount, LocalDate date) {
        Savings s = savings.get(savingsAccountId);
        if (s.balance.compareTo(amount) < 0) {
            throw new FineractException("balance going negative");
        }
        s.balance = s.balance.subtract(amount);
        s.feeOutstanding = s.feeOutstanding.subtract(amount);
        s.transactions.add(new SavingsTransaction(ids.incrementAndGet(), date, amount, false, false, false));
        postings.add("pay-fee:" + savingsAccountId + ":" + amount.toPlainString());
    }

    @Override
    public long withdrawToSupplier(long savingsAccountId, BigDecimal amount, LocalDate date, String note) {
        Savings s = savings.get(savingsAccountId);
        long id = ids.incrementAndGet();
        s.balance = s.balance.subtract(amount);
        s.transactions.add(new SavingsTransaction(id, date, amount, false, true, false));
        postings.add("withdraw:" + savingsAccountId + ":" + amount.toPlainString());
        maybeFail("afterWithdraw");
        return id;
    }

    @Override
    public Optional<Long> findLoanByExternalId(String externalId) {
        return loans.entrySet().stream().filter(e -> externalId.equals(e.getValue().externalId))
                .map(Map.Entry::getKey).findFirst();
    }

    @Override
    public long createAssetLoan(long clientId, BigDecimal principal, String externalId, LocalDate date) {
        long id = ids.incrementAndGet();
        Loan l = new Loan();
        l.clientId = clientId;
        l.externalId = externalId;
        l.principal = principal;
        loans.put(id, l);
        postings.add("create-loan:" + id + ":" + principal.toPlainString());
        return id;
    }

    @Override
    public void approveLoan(long loanId, LocalDate date) {
        maybeFail("approveLoan");
        loans.get(loanId).status = "approved";
        postings.add("approve-loan:" + loanId);
    }

    @Override
    public void disburseLoanToSupplier(long loanId, BigDecimal amount, LocalDate date, String note) {
        loans.get(loanId).status = "active";
        postings.add("disburse-loan:" + loanId + ":" + amount.toPlainString());
    }

    @Override
    public LoanInfo getLoan(long loanId) {
        Loan l = loans.get(loanId);
        return new LoanInfo(loanId, l.status, l.status.equals("pending"), l.status.equals("approved"),
                l.status.equals("active"), l.status.equals("closed"), l.principal, BigDecimal.ZERO);
    }

    @Override
    public Optional<AuthenticatedUser> authenticate(String username, String password) {
        return Optional.empty();
    }
}
