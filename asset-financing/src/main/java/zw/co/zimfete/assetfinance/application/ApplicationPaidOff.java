package zw.co.zimfete.assetfinance.application;

/** Published when an application is fully paid, so the asset register can transfer ownership. */
public record ApplicationPaidOff(long applicationId) {
}
