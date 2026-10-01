package zw.co.zimfete.assetfinance;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestConfig {

    public static final ZoneId HARARE = ZoneId.of("Africa/Harare");

    @Bean
    @Primary
    FakeFineractClient fakeFineract() {
        return new FakeFineractClient();
    }

    /** "Today" is 1 October 2026, 10:00 in Harare. */
    @Bean
    @Primary
    Clock testClock() {
        return Clock.fixed(Instant.parse("2026-10-01T08:00:00Z"), HARARE);
    }
}
