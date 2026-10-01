package zw.co.zimfete.assetfinance.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import zw.co.zimfete.assetfinance.fineract.FineractClient.SavingsTransaction;

/** How close a member is to their deposit target, and when they are likely to reach it. */
public record Progress(BigDecimal target, BigDecimal balance, BigDecimal remaining, BigDecimal percentComplete,
                       BigDecimal averageMonthlyDeposit, LocalDate estimatedTargetDate) {

    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30.44");

    public static BigDecimal percent(BigDecimal target, BigDecimal balance) {
        return target.signum() == 0 ? BigDecimal.valueOf(100)
                : balance.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.DOWN);
    }

    /**
     * The average uses deposits from the last {@code windowMonths} months (or since the first
     * deposit, if more recent). The estimate assumes the member keeps depositing at that pace.
     */
    public static Progress calculate(BigDecimal target, BigDecimal balance, List<SavingsTransaction> transactions,
                                     LocalDate today, int windowMonths) {
        BigDecimal remaining = target.subtract(balance).max(BigDecimal.ZERO);
        BigDecimal percent = percent(target, balance);

        List<SavingsTransaction> deposits = transactions.stream()
                .filter(t -> t.deposit() && !t.reversed() && t.date() != null && !t.date().isAfter(today))
                .toList();
        BigDecimal average = BigDecimal.ZERO;
        if (!deposits.isEmpty()) {
            LocalDate windowStart = today.minusMonths(windowMonths);
            LocalDate firstDeposit = deposits.stream().map(SavingsTransaction::date).min(LocalDate::compareTo).get();
            LocalDate from = firstDeposit.isAfter(windowStart) ? firstDeposit : windowStart;
            BigDecimal inWindow = deposits.stream().filter(t -> !t.date().isBefore(from))
                    .map(SavingsTransaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal months = BigDecimal.valueOf(ChronoUnit.DAYS.between(from, today))
                    .divide(DAYS_PER_MONTH, 4, RoundingMode.HALF_UP).max(BigDecimal.ONE);
            average = inWindow.divide(months, 2, RoundingMode.HALF_UP);
        }

        LocalDate estimate = null;
        if (remaining.signum() == 0) {
            estimate = today;
        } else if (average.signum() > 0) {
            long days = remaining.multiply(DAYS_PER_MONTH).divide(average, 0, RoundingMode.CEILING).longValue();
            estimate = today.plusDays(days);
        }
        return new Progress(target, balance, remaining, percent, average, estimate);
    }
}
