package zw.co.zimfete.assetfinance.application;

/**
 * SAVING → QUALIFIED → PROCUREMENT → DELIVERED → REPAYING → PAID_OFF.
 * CANCELLED is possible while SAVING or QUALIFIED.
 */
public enum ApplicationStatus {
    /** Member is depositing toward the deposit target (50% of the asset cost). */
    SAVING,
    /** Target reached; member is in the waiting queue. */
    QUALIFIED,
    /** A purchase order has been approved; the asset is being bought/installed. */
    PROCUREMENT,
    /** Asset delivered and confirmed in the field; waiting for conversion into a loan. */
    DELIVERED,
    /** Deposit applied and the balance disbursed as a Fineract loan; member is repaying. */
    REPAYING,
    /** Loan closed in Fineract (or the deposit covered the full cost); ownership passes to the member. */
    PAID_OFF,
    CANCELLED;

    public boolean isOpen() {
        return this == SAVING || this == QUALIFIED;
    }
}
