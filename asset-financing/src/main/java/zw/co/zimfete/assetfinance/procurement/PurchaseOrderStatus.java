package zw.co.zimfete.assetfinance.procurement;

public enum PurchaseOrderStatus {
    DRAFT, APPROVED, COMPLETED, CANCELLED;

    public boolean isActive() {
        return this == DRAFT || this == APPROVED;
    }
}
