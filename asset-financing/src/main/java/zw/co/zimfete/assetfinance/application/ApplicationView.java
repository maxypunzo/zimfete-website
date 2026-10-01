package zw.co.zimfete.assetfinance.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** What the API returns for an application. */
public record ApplicationView(
        Long id, String reference, long clientId, String memberName, long officeId, Long officerStaffId,
        Long catalogueItemId, String assetName, Long quoteId, BigDecimal assetCost, String currency,
        BigDecimal depositPercent, BigDecimal depositTarget, BigDecimal depositedAmount, BigDecimal percentComplete,
        BigDecimal avgMonthlyDeposit, LocalDate estimatedTargetDate, Instant balanceSyncedAt,
        ApplicationStatus status, LocalDate openedOn, Instant qualifiedAt, long savingsAccountId,
        ConversionStep conversionStep, BigDecimal depositApplied, BigDecimal financedAmount, Long loanId,
        String cancelReason, String createdBy) {

    public static ApplicationView of(AssetApplication a) {
        BigDecimal percent = Progress.percent(a.getDepositTarget(), a.getDepositedAmount());
        return new ApplicationView(a.getId(), a.getReference(), a.getFineractClientId(), a.getMemberName(),
                a.getOfficeId(), a.getOfficerStaffId(), a.getCatalogueItem().getId(), a.getCatalogueItem().getName(),
                a.getQuote() == null ? null : a.getQuote().getId(), a.getAssetCost(), a.getCurrency(),
                a.getDepositPercent(), a.getDepositTarget(), a.getDepositedAmount(), percent,
                a.getAvgMonthlyDeposit(), a.getEstimatedTargetDate(), a.getBalanceSyncedAt(), a.getStatus(),
                a.getOpenedOn(), a.getQualifiedAt(), a.getFineractSavingsAccountId(), a.getConversionStep(),
                a.getDepositApplied(), a.getFinancedAmount(), a.getFineractLoanId(), a.getCancelReason(),
                a.getCreatedBy());
    }
}
