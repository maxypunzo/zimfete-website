package zw.co.zimfete.assetfinance.fineract.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import zw.co.zimfete.assetfinance.fineract.FineractClient;
import zw.co.zimfete.assetfinance.fineract.FineractException;

/**
 * A pretend Fineract held in memory, for staff training and trying the screens without a Fineract
 * server. Everything is lost on restart. Enabled only with zimfete.fineract.mode=demo together with
 * the built-in development users, so it can never be mixed with real member data.
 */
public class DemoFineractClient implements FineractClient {

    private final AtomicLong ids = new AtomicLong(5000);
    private final Map<Long, Office> offices = new LinkedHashMap<>();
    private final Map<Long, ClientInfo> clients = new LinkedHashMap<>();
    private final Map<Long, Savings> savings = new LinkedHashMap<>();
    private final Map<Long, Loan> loans = new LinkedHashMap<>();

    private static final class Savings {
        long clientId;
        BigDecimal balance = BigDecimal.ZERO;
        final List<SavingsTransaction> transactions = new ArrayList<>();
    }

    private static final class Loan {
        long clientId;
        String externalId;
        BigDecimal principal;
        BigDecimal outstanding;
        String status = "Submitted and pending approval";
    }

    public DemoFineractClient() {
        String[] names = { "Head Office", "Marondera", "Mackeche", "Hwedza", "Seke", "Murewa", "Mutoko", "Mudzi",
                "UMP" };
        for (int i = 0; i < names.length; i++) {
            offices.put((long) i + 1, new Office(i + 1, names[i]));
        }
        String[][] members = {
                { "Tendai Moyo", "2" }, { "Farai Ncube", "2" }, { "Chipo Mutasa", "2" },
                { "Tatenda Gumbo", "3" }, { "Nyasha Sibanda", "3" },
                { "Rudo Chikwanha", "4" }, { "Kudakwashe Marufu", "4" },
                { "Tsitsi Mhlanga", "5" }, { "Blessing Chari", "5" },
                { "Simbarashe Dube", "6" }, { "Ruvimbo Nyoni", "6" },
                { "Tafadzwa Zhou", "7" }, { "Memory Chinembiri", "7" },
                { "Takudzwa Mapfumo", "8" }, { "Precious Mupfumi", "8" },
                { "Nokuthula Banda", "9" }, { "Tinashe Makoni", "9" } };
        long id = 101;
        for (String[] m : members) {
            clients.put(id, new ClientInfo(id, m[0], Long.parseLong(m[1]), null, true));
            id++;
        }
    }

    // --- Demo-only actions, standing in for what tellers would do in Mifos ---

    public synchronized void deposit(long savingsId, BigDecimal amount, LocalDate date) {
        Savings s = savingsAccount(savingsId);
        s.balance = s.balance.add(amount);
        s.transactions.add(new SavingsTransaction(ids.incrementAndGet(), date, amount, true, false, false));
    }

    public synchronized void repayInFull(long loanId) {
        Loan l = loan(loanId);
        if (!"Active".equals(l.status)) {
            throw new FineractException("Loan " + loanId + " is not active");
        }
        l.outstanding = BigDecimal.ZERO;
        l.status = "Closed (obligations met)";
    }

    // --- FineractClient ---

    @Override
    public synchronized ClientInfo getClient(long clientId) {
        ClientInfo c = clients.get(clientId);
        if (c == null) {
            throw new FineractException("Fineract GET /clients/" + clientId + " failed: 404 client not found");
        }
        return c;
    }

    @Override
    public synchronized List<Office> listOffices() {
        return List.copyOf(offices.values());
    }

    @Override
    public synchronized List<ClientSummary> searchClients(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        return clients.values().stream()
                .filter(c -> c.displayName().toLowerCase(Locale.ROOT).contains(q) || String.valueOf(c.id()).equals(q))
                .map(c -> new ClientSummary(c.id(), c.displayName(), String.format("%09d", c.id()), c.officeId(),
                        offices.get(c.officeId()).name(), null, c.active()))
                .toList();
    }

    @Override
    public synchronized long openAssetDepositAccount(long clientId, String externalId, LocalDate date) {
        getClient(clientId);
        long id = ids.incrementAndGet();
        Savings s = new Savings();
        s.clientId = clientId;
        savings.put(id, s);
        return id;
    }

    @Override
    public synchronized SavingsAccountInfo getSavingsAccount(long savingsAccountId) {
        Savings s = savingsAccount(savingsAccountId);
        return new SavingsAccountInfo(savingsAccountId, s.clientId, "USD", s.balance, List.copyOf(s.transactions));
    }

    @Override
    public synchronized long withdrawToSupplier(long savingsAccountId, BigDecimal amount, LocalDate date, String note) {
        Savings s = savingsAccount(savingsAccountId);
        if (s.balance.compareTo(amount) < 0) {
            throw new FineractException("Insufficient balance in savings account " + savingsAccountId);
        }
        long id = ids.incrementAndGet();
        s.balance = s.balance.subtract(amount);
        s.transactions.add(new SavingsTransaction(id, date, amount, false, true, false));
        return id;
    }

    @Override
    public synchronized Optional<Long> findLoanByExternalId(String externalId) {
        return loans.entrySet().stream().filter(e -> externalId.equals(e.getValue().externalId))
                .map(Map.Entry::getKey).findFirst();
    }

    @Override
    public synchronized long createAssetLoan(long clientId, BigDecimal principal, String externalId, LocalDate date) {
        long id = ids.incrementAndGet();
        Loan l = new Loan();
        l.clientId = clientId;
        l.externalId = externalId;
        l.principal = principal;
        l.outstanding = principal;
        loans.put(id, l);
        return id;
    }

    @Override
    public synchronized void approveLoan(long loanId, LocalDate date) {
        loan(loanId).status = "Approved";
    }

    @Override
    public synchronized void disburseLoanToSupplier(long loanId, BigDecimal amount, LocalDate date, String note) {
        loan(loanId).status = "Active";
    }

    @Override
    public synchronized LoanInfo getLoan(long loanId) {
        Loan l = loan(loanId);
        return new LoanInfo(loanId, l.status, l.status.startsWith("Submitted"), "Approved".equals(l.status),
                "Active".equals(l.status), l.status.startsWith("Closed"), l.principal, l.outstanding);
    }

    @Override
    public Optional<AuthenticatedUser> authenticate(String username, String password) {
        return Optional.empty();
    }

    private Savings savingsAccount(long id) {
        Savings s = savings.get(id);
        if (s == null) {
            throw new FineractException("Savings account " + id + " not found");
        }
        return s;
    }

    private Loan loan(long id) {
        Loan l = loans.get(id);
        if (l == null) {
            throw new FineractException("Loan " + id + " not found");
        }
        return l;
    }
}
