package zw.co.zimfete.assetfinance.config;

import java.time.Clock;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import zw.co.zimfete.assetfinance.fineract.FineractClient;
import zw.co.zimfete.assetfinance.fineract.FineractRestClient;
import zw.co.zimfete.assetfinance.fineract.demo.DemoFineractClient;

@Configuration
public class FineractConfig {

    private static final Logger log = LoggerFactory.getLogger(FineractConfig.class);

    @Bean
    FineractClient fineractClient(ZimfeteProperties properties) {
        if (properties.fineract().isDemo()) {
            if (!"in-memory".equals(properties.security().provider())) {
                throw new IllegalStateException("zimfete.fineract.mode=demo is only allowed with the built-in "
                        + "development users (zimfete.security.provider=in-memory)");
            }
            log.warn("DEMO MODE: using a pretend Fineract held in memory. No real money is involved.");
            return new DemoFineractClient();
        }
        return new FineractRestClient(RestClient.builder(), properties.fineract());
    }

    /** Africa/Harare business dates; injectable so tests can fix "today". */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Africa/Harare"));
    }
}
