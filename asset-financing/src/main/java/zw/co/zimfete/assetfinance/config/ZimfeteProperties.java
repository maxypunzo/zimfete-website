package zw.co.zimfete.assetfinance.config;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything ZimFete-specific that differs between test and production lives here,
 * so the Fineract IDs from your own installation can be set without code changes.
 */
@ConfigurationProperties(prefix = "zimfete")
public record ZimfeteProperties(Fineract fineract, Policy policy, Security security, Storage storage) {

    public record Fineract(
            /** e.g. http://localhost:8080/fineract-provider/api/v1 */
            String baseUrl,
            String tenantId,
            /** Service account used for all postings made by this module. */
            String username,
            String password,
            /** Savings product configured as the "Asset Deposit Account" (with the account opening fee as an activation charge). */
            long assetDepositProductId,
            /** Loan product for the financed balance of the asset. */
            long assetLoanProductId,
            /** Fineract payment type used when paying a supplier (deposit withdrawal and loan disbursement). */
            long supplierPaymentTypeId,
            /** Optional Fineract fund the asset loans are tagged with (e.g. "Retained profit"). */
            Long assetLoanFundId,
            /** Shared secret that is part of the hook URL: /api/webhooks/fineract/{token}/ */
            String webhookToken,
            /** "live" (real Fineract) or "demo" (built-in pretend Fineract for training; never with real members). */
            String mode,
            /**
             * Optional savings charge (type "Specified due date") for the account opening fee. It is attached
             * when the account is opened and collected from the member's deposits once they cover it.
             */
            Long openingFeeChargeId) {

        public boolean isDemo() {
            return "demo".equals(mode);
        }
    }

    public record Policy(
            /** Percentage of the asset cost the member must deposit before qualifying (50). */
            BigDecimal depositPercent,
            /** How the waiting queue is ordered once members reach their target. */
            QueueRule queueRule,
            /** How many months of deposit history to use for the "expected qualifying date". */
            int forecastWindowMonths,
            /** Fineract office id of Head Office; users there see all locations. */
            long headOfficeId) {
    }

    public enum QueueRule {
        /** First member to reach the deposit target is served first. */
        FIRST_QUALIFIED,
        /** Earliest application is served first, among those who have qualified. */
        FIRST_OPENED
    }

    public record Security(
            /** "fineract" (log in with Fineract users) or "in-memory" (local development only). */
            String provider,
            /** Fineract role name -> module role (OFFICER, MANAGER, ADMIN). */
            Map<String, String> roleMapping) {
    }

    public record Storage(String photoDirectory) {
    }
}
