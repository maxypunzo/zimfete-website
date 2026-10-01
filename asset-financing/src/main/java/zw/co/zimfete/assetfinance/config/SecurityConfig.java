package zw.co.zimfete.assetfinance.config;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import zw.co.zimfete.assetfinance.fineract.FineractClient;
import zw.co.zimfete.assetfinance.security.DevAuthenticationProvider;
import zw.co.zimfete.assetfinance.security.FineractAuthenticationProvider;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                // Stateless JSON API using HTTP Basic: no cookies, so CSRF protection does not apply.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**").permitAll()
                        // Fineract hooks cannot log in; they are checked with a shared token instead.
                        .requestMatchers("/api/webhooks/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    AuthenticationProvider authenticationProvider(ZimfeteProperties properties, FineractClient fineract, Clock clock) {
        if ("in-memory".equals(properties.security().provider())) {
            log.warn("Using built-in development users. Never use zimfete.security.provider=in-memory on a server.");
            return new DevAuthenticationProvider();
        }
        return new FineractAuthenticationProvider(fineract, properties.security().roleMapping(), clock);
    }
}
