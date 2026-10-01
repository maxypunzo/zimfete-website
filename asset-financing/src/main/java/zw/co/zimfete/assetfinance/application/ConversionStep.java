package zw.co.zimfete.assetfinance.application;

/**
 * Progress of the multi-step conversion in Fineract. Each step is saved as soon as it succeeds,
 * so a conversion interrupted half-way (network, Fineract down) resumes where it stopped instead
 * of posting money twice.
 */
public enum ConversionStep {
    NOT_STARTED,
    /** Intent saved: we are about to withdraw this amount from the deposit account. */
    APPLYING_DEPOSIT,
    DEPOSIT_APPLIED,
    LOAN_CREATED,
    LOAN_APPROVED,
    LOAN_DISBURSED
}
