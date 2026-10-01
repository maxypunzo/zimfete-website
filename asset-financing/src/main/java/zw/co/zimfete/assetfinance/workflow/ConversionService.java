package zw.co.zimfete.assetfinance.workflow;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import zw.co.zimfete.assetfinance.application.ApplicationPaidOff;
import zw.co.zimfete.assetfinance.application.ApplicationStatus;
import zw.co.zimfete.assetfinance.application.AssetApplication;
import zw.co.zimfete.assetfinance.application.AssetApplicationRepository;
import zw.co.zimfete.assetfinance.application.ConversionStep;
import zw.co.zimfete.assetfinance.fineract.FineractClient;
import zw.co.zimfete.assetfinance.fineract.FineractClient.LoanInfo;
import zw.co.zimfete.assetfinance.fineract.FineractClient.SavingsAccountInfo;
import zw.co.zimfete.assetfinance.procurement.PurchaseOrderRepository;
import zw.co.zimfete.assetfinance.procurement.PurchaseOrderStatus;
import zw.co.zimfete.assetfinance.security.AppUser;
import zw.co.zimfete.assetfinance.security.OfficeAccess;
import zw.co.zimfete.assetfinance.web.BusinessRuleException;
import zw.co.zimfete.assetfinance.web.NotFoundException;

/**
 * Turns a delivered asset into a loan, in Fineract:
 * <ol>
 *   <li>Withdraw the member's deposit from the Asset Deposit Account (payment type "supplier").</li>
 *   <li>Create a loan for the rest of the cost (asset cost − deposit), tagged with the application reference.</li>
 *   <li>Approve it.</li>
 *   <li>Disburse it to the supplier.</li>
 * </ol>
 * Together, steps 1 and 4 equal the full price paid to the supplier.
 *
 * Fineract calls cannot share a database transaction with this module, so each completed step is
 * saved immediately. Running convert again after a failure resumes at the step that failed, and
 * checks Fineract first so that nothing is posted twice.
 */
@Service
public class ConversionService {

    private static final Logger log = LoggerFactory.getLogger(ConversionService.class);

    private final AssetApplicationRepository applications;
    private final PurchaseOrderRepository orders;
    private final FineractClient fineract;
    private final OfficeAccess officeAccess;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;
    /** One conversion at a time per application. Assumes a single running instance of this module. */
    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ConversionService(AssetApplicationRepository applications, PurchaseOrderRepository orders,
                             FineractClient fineract, OfficeAccess officeAccess, ApplicationEventPublisher events,
                             TransactionTemplate tx, Clock clock) {
        this.applications = applications;
        this.orders = orders;
        this.fineract = fineract;
        this.officeAccess = officeAccess;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    public AssetApplication convert(long applicationId, AppUser user) {
        ReentrantLock lock = locks.computeIfAbsent(applicationId, id -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new BusinessRuleException("A conversion for this application is already running");
        }
        try {
            return runConversion(applicationId, user);
        } finally {
            lock.unlock();
        }
    }

    private AssetApplication runConversion(long id, AppUser user) {
        LocalDate today = LocalDate.now(clock);
        AssetApplication app = load(id);
        officeAccess.check(user, app.getOfficeId());

        if (app.getConversionStep() == ConversionStep.NOT_STARTED) {
            BigDecimal balance = fineract.getSavingsAccount(app.getFineractSavingsAccountId()).balance();
            app = update(id, a -> a.beginApplyingDeposit(balance));
            log.info("{}: applying deposit {} to asset cost {}", app.getReference(), app.getDepositApplied(),
                    app.getAssetCost());
        } else if (app.getStatus() != ApplicationStatus.DELIVERED) {
            throw new BusinessRuleException("Application " + app.getReference() + " has already been converted ("
                    + app.getStatus() + ")");
        } else {
            log.info("{}: resuming conversion at step {}", app.getReference(), app.getConversionStep());
        }

        if (app.getConversionStep() == ConversionStep.APPLYING_DEPOSIT) {
            AssetApplication current = app;
            long txId = existingWithdrawal(current).orElseGet(() -> fineract.withdrawToSupplier(
                    current.getFineractSavingsAccountId(), current.getDepositApplied(), today,
                    "Deposit applied to asset " + current.getReference()));
            app = update(id, a -> a.depositApplied(txId));
            if (app.getStatus() == ApplicationStatus.PAID_OFF) {
                // Deposit covered the whole price: no loan needed.
                finish(id, true);
                return load(id);
            }
        }

        if (app.getConversionStep() == ConversionStep.DEPOSIT_APPLIED) {
            AssetApplication current = app;
            long loanId = fineract.findLoanByExternalId(current.getReference()).orElseGet(() ->
                    fineract.createAssetLoan(current.getFineractClientId(), current.getFinancedAmount(),
                            current.getReference(), today));
            app = update(id, a -> a.loanCreated(loanId));
        }

        if (app.getConversionStep() == ConversionStep.LOAN_CREATED) {
            LoanInfo loan = fineract.getLoan(app.getFineractLoanId());
            if (loan.pendingApproval()) {
                fineract.approveLoan(app.getFineractLoanId(), today);
            }
            app = update(id, AssetApplication::loanApproved);
        }

        if (app.getConversionStep() == ConversionStep.LOAN_APPROVED) {
            LoanInfo loan = fineract.getLoan(app.getFineractLoanId());
            if (loan.waitingForDisbursal()) {
                fineract.disburseLoanToSupplier(app.getFineractLoanId(), app.getFinancedAmount(), today,
                        "Asset " + app.getReference() + " paid to supplier");
            } else if (!loan.active()) {
                throw new BusinessRuleException("Loan " + app.getFineractLoanId() + " is '" + loan.status()
                        + "' in Fineract; resolve it in Mifos before converting again");
            }
            update(id, AssetApplication::loanDisbursed);
            finish(id, false);
        }
        return load(id);
    }

    /** If a previous attempt withdrew the deposit but crashed before saving, reuse that transaction. */
    private Optional<Long> existingWithdrawal(AssetApplication app) {
        SavingsAccountInfo account = fineract.getSavingsAccount(app.getFineractSavingsAccountId());
        if (account.balance().compareTo(app.getDepositApplied()) >= 0) {
            return Optional.empty(); // the money is still there, so it was not withdrawn
        }
        return account.transactions().stream()
                .filter(t -> t.withdrawal() && !t.reversed() && t.amount().compareTo(app.getDepositApplied()) == 0
                        && t.date() != null && !t.date().isBefore(app.getOpenedOn()))
                .map(FineractClient.SavingsTransaction::id)
                .reduce((first, second) -> second);
    }

    private void finish(long id, boolean paidOff) {
        tx.executeWithoutResult(s -> {
            orders.findFirstByApplicationIdAndStatus(id, PurchaseOrderStatus.APPROVED)
                    .ifPresent(po -> po.complete(clock.instant()));
            if (paidOff) {
                events.publishEvent(new ApplicationPaidOff(id));
            }
        });
    }

    private AssetApplication load(long id) {
        return tx.execute(s -> {
            AssetApplication app = applications.findById(id).orElseThrow(() -> new NotFoundException("Application", id));
            app.getCatalogueItem().getName(); // initialise for use after the transaction
            return app;
        });
    }

    private AssetApplication update(long id, Consumer<AssetApplication> change) {
        return tx.execute(s -> {
            AssetApplication app = applications.findById(id).orElseThrow(() -> new NotFoundException("Application", id));
            change.accept(app);
            app.getCatalogueItem().getName();
            return applications.saveAndFlush(app);
        });
    }
}
