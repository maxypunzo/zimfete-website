package zw.co.zimfete.assetfinance.fineract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The only operations the asset module needs from Fineract. Keeping this narrow makes the
 * module easy to test without a running Fineract, and documents exactly what it touches.
 */
public interface FineractClient {

    ClientInfo getClient(long clientId);

    List<Office> listOffices();

    /** Finds members by name, account number, external id or phone. */
    List<ClientSummary> searchClients(String query);

    /** Opens, approves and activates an Asset Deposit savings account. Returns its id. */
    long openAssetDepositAccount(long clientId, String externalId, LocalDate date);

    SavingsAccountInfo getSavingsAccount(long savingsAccountId);

    /** Collects a charge that is due on a savings account (e.g. the opening fee) from its balance. */
    void paySavingsCharge(long savingsAccountId, long accountChargeId, BigDecimal amount, LocalDate date);

    /** Withdraws from the deposit account to pay the supplier. Returns the transaction id. */
    long withdrawToSupplier(long savingsAccountId, BigDecimal amount, LocalDate date, String note);

    Optional<Long> findLoanByExternalId(String externalId);

    /** Creates the asset loan application for the financed balance. Returns the loan id. */
    long createAssetLoan(long clientId, BigDecimal principal, String externalId, LocalDate date);

    void approveLoan(long loanId, LocalDate date);

    void disburseLoanToSupplier(long loanId, BigDecimal amount, LocalDate date, String note);

    LoanInfo getLoan(long loanId);

    /** Verifies a user's credentials and returns their office and roles. */
    Optional<AuthenticatedUser> authenticate(String username, String password);

    record Office(long id, String name) {
    }

    record ClientSummary(long id, String displayName, String accountNo, long officeId, String officeName,
                         String mobileNo, boolean active) {
    }

    record ClientInfo(long id, String displayName, long officeId, Long staffId, boolean active) {
    }

    record SavingsAccountInfo(long id, long clientId, String currency, BigDecimal balance,
                              List<SavingsTransaction> transactions, List<AccountCharge> charges) {
    }

    /** A charge on a savings account: {@code id} is the account-charge id, {@code chargeId} the charge definition. */
    record AccountCharge(long id, long chargeId, BigDecimal outstanding) {
    }

    record SavingsTransaction(long id, LocalDate date, BigDecimal amount, boolean deposit,
                              boolean withdrawal, boolean reversed) {
    }

    record LoanInfo(long id, String status, boolean pendingApproval, boolean waitingForDisbursal, boolean active,
                    boolean closed, BigDecimal principal, BigDecimal outstanding) {
    }

    record AuthenticatedUser(String username, long officeId, Long staffId, List<String> roles) {
    }
}
