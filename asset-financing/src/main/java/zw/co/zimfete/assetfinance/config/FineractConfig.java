package zw.co.zimfete.assetfinance.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import zw.co.zimfete.assetfinance.fineract.FineractClient;
import zw.co.zimfete.assetfinance.fineract.FineractRestClient;

@Configuration
public class FineractConfig {

    @Bean
    FineractClient fineractClient(ZimfeteProperties properties) {
        return new FineractRestClient(RestClient.builder(), properties.fineract());
    }

    /** Africa/Harare business dates; injectable so tests can fix "today". */
    @Bean
    Clock clock() {
        return Clock.system(java.time.ZoneId.of("Africa/Harare"));
    }
}
