package zw.co.zimfete.assetfinance.config;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import zw.co.zimfete.assetfinance.fineract.FineractClient;
import zw.co.zimfete.assetfinance.security.DevAuthenticationProvider;
import zw.co.zimfete.assetfinance.security.FineractAuthenticationProvider;

/**
 * Two ways in:
 * <ul>
 *   <li>The staff web app logs in once (POST /api/auth/login) and then uses an HttpOnly session
 *       cookie, protected against CSRF with the double-submit XSRF-TOKEN cookie.</li>
 *   <li>Scripts and Swagger UI may send HTTP Basic credentials on every request. Browsers never add
 *       an Authorization header by themselves (no Basic challenge is ever sent), so such requests
 *       cannot be forged cross-site and skip the CSRF check.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.spa()
                        .ignoringRequestMatchers("/api/webhooks/**")
                        .ignoringRequestMatchers(request -> request.getHeader("Authorization") != null))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/csrf").permitAll()
                        // Fineract hooks cannot log in; they are checked with a shared token instead.
                        .requestMatchers("/api/webhooks/**").permitAll()
                        .anyRequest().authenticated())
                // 401 without a "WWW-Authenticate: Basic" header, so browsers never show a password pop-up.
                .httpBasic(basic -> basic.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
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

    @Bean
    AuthenticationManager authenticationManager(AuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
