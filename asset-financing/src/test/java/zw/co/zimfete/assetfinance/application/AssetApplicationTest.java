package zw.co.zimfete.assetfinance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import zw.co.zimfete.assetfinance.catalogue.AssetCatalogueItem;
import zw.co.zimfete.assetfinance.catalogue.AssetCategory;
import zw.co.zimfete.assetfinance.web.BusinessRuleException;

class AssetApplicationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");

    private static AssetApplication borehole(String cost) {
        AssetCatalogueItem item = new AssetCatalogueItem("BH", "Borehole", AssetCategory.BOREHOLE, null,
                new BigDecimal(cost), "USD");
        return new AssetApplication("AF-TEST0001", 1, "Member", 2, null, item, null, new BigDecimal(cost), "USD",
                new BigDecimal("50"), 99, TODAY, "officer");
    }

    private static Progress balance(AssetApplication app, String amount) {
        return Progress.calculate(app.getDepositTarget(), new BigDecimal(amount), List.of(), TODAY, 6);
    }

    @Test
    void targetIsHalfTheCost() {
        assertThat(borehole("4000").getDepositTarget()).isEqualByComparingTo("2000.00");
        assertThat(borehole("3999.99").getDepositTarget()).isEqualByComparingTo("2000.00");
    }

    @Test
    void qualifiesOnceWhenTargetReached() {
        AssetApplication app = borehole("4000");

        assertThat(app.recordBalance(balance(app, "1999.99"), NOW)).isFalse();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SAVING);

        assertThat(app.recordBalance(balance(app, "2000"), NOW)).isTrue();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.QUALIFIED);
        assertThat(app.getQualifiedAt()).isEqualTo(NOW);

        // A later sync must not re-notify or move the queue position.
        assertThat(app.recordBalance(balance(app, "2500"), NOW.plusSeconds(60))).isFalse();
        assertThat(app.getQualifiedAt()).isEqualTo(NOW);
    }

    @Test
    void dropsOutOfQueueIfBalanceFallsBelowTarget() {
        AssetApplication app = borehole("4000");
        app.recordBalance(balance(app, "2000"), NOW);

        app.recordBalance(balance(app, "1500"), NOW);

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SAVING);
        assertThat(app.getQualifiedAt()).isNull();
    }

    @Test
    void priceIncreaseCanTakeMemberOutOfQueue() {
        AssetApplication app = borehole("4000");
        app.recordBalance(balance(app, "2000"), NOW);

        app.reprice(new BigDecimal("4600"), null, NOW);

        assertThat(app.getDepositTarget()).isEqualByComparingTo("2300.00");
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SAVING);
    }

    @Test
    void cannotSkipProcurement() {
        AssetApplication app = borehole("4000");
        app.recordBalance(balance(app, "2000"), NOW);

        assertThatThrownBy(app::markDelivered).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void depositCoveringFullCostNeedsNoLoan() {
        AssetApplication app = borehole("4000");
        app.recordBalance(balance(app, "4200"), NOW);
        app.startProcurement();
        app.markDelivered();

        app.beginApplyingDeposit(new BigDecimal("4200"));
        app.depositApplied(5);

        assertThat(app.getDepositApplied()).isEqualByComparingTo("4000");
        assertThat(app.getFinancedAmount()).isEqualByComparingTo("0");
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.PAID_OFF);
    }

    @Test
    void conversionRefusedIfDepositFellBelowTarget() {
        AssetApplication app = borehole("4000");
        app.recordBalance(balance(app, "2000"), NOW);
        app.startProcurement();
        app.markDelivered();

        assertThatThrownBy(() -> app.beginApplyingDeposit(new BigDecimal("1800")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("below the target");
    }
}
