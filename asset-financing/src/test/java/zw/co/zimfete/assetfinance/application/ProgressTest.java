package zw.co.zimfete.assetfinance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import zw.co.zimfete.assetfinance.fineract.FineractClient.SavingsTransaction;

class ProgressTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    private static SavingsTransaction deposit(LocalDate date, String amount) {
        return new SavingsTransaction(1, date, new BigDecimal(amount), true, false, false);
    }

    @Test
    void estimatesQualifyingDateFromRecentDepositPace() {
        // $100 a month for 6 months; target $2000, balance $600 -> $1400 left -> ~14 months.
        List<SavingsTransaction> txs = List.of(
                deposit(TODAY.minusMonths(6), "100"), deposit(TODAY.minusMonths(5), "100"),
                deposit(TODAY.minusMonths(4), "100"), deposit(TODAY.minusMonths(3), "100"),
                deposit(TODAY.minusMonths(2), "100"), deposit(TODAY.minusMonths(1), "100"));

        Progress p = Progress.calculate(new BigDecimal("2000.00"), new BigDecimal("600.00"), txs, TODAY, 6);

        assertThat(p.remaining()).isEqualByComparingTo("1400");
        assertThat(p.percentComplete()).isEqualByComparingTo("30.0");
        // 6 months = 183 days here, so the pace is just under $100/month.
        assertThat(p.averageMonthlyDeposit()).isCloseTo(new BigDecimal("100"), within(new BigDecimal("0.5")));
        assertThat(p.estimatedTargetDate()).isBetween(LocalDate.of(2027, 11, 15), LocalDate.of(2027, 12, 15));
    }

    @Test
    void ignoresReversedDepositsAndWithdrawals() {
        List<SavingsTransaction> txs = List.of(
                deposit(TODAY.minusMonths(1), "500"),
                new SavingsTransaction(2, TODAY.minusDays(10), new BigDecimal("900"), true, false, true),
                new SavingsTransaction(3, TODAY.minusDays(5), new BigDecimal("200"), false, true, false));

        Progress p = Progress.calculate(new BigDecimal("2000"), new BigDecimal("300"), txs, TODAY, 6);

        assertThat(p.averageMonthlyDeposit()).isEqualByComparingTo("500.00");
    }

    @Test
    void noDepositsMeansNoEstimate() {
        Progress p = Progress.calculate(new BigDecimal("2000"), BigDecimal.ZERO, List.of(), TODAY, 6);

        assertThat(p.estimatedTargetDate()).isNull();
        assertThat(p.percentComplete()).isEqualByComparingTo("0");
    }

    @Test
    void targetReachedIsToday() {
        Progress p = Progress.calculate(new BigDecimal("2000"), new BigDecimal("2100"), List.of(), TODAY, 6);

        assertThat(p.remaining()).isEqualByComparingTo("0");
        assertThat(p.estimatedTargetDate()).isEqualTo(TODAY);
        assertThat(p.percentComplete()).isEqualByComparingTo("105.0");
    }
}
