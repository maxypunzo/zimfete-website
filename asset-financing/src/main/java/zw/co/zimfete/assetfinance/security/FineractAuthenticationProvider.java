package zw.co.zimfete.assetfinance.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import zw.co.zimfete.assetfinance.fineract.FineractClient;
import zw.co.zimfete.assetfinance.fineract.FineractException;

/**
 * Checks credentials against Fineract's /authentication endpoint, so staff use one login for
 * both Mifos and this module. Fineract role names are mapped to module roles through
 * {@code zimfete.security.role-mapping}. Successful logins are cached briefly so every API call
 * does not hit Fineract.
 */
public class FineractAuthenticationProvider implements AuthenticationProvider {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final FineractClient fineract;
    private final Map<String, Role> roleMapping;
    private final Clock clock;
    private final Map<String, CachedLogin> cache = new ConcurrentHashMap<>();

    public FineractAuthenticationProvider(FineractClient fineract, Map<String, String> roleMapping, Clock clock) {
        this.fineract = fineract;
        this.clock = clock;
        this.roleMapping = new ConcurrentHashMap<>();
        roleMapping.forEach((fineractRole, role) -> this.roleMapping.put(fineractRole.toLowerCase(), Role.valueOf(role)));
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String username = authentication.getName();
        String password = String.valueOf(authentication.getCredentials());
        byte[] passwordHash = sha256(password);

        CachedLogin cached = cache.get(username);
        if (cached != null && cached.expires.isAfter(clock.instant())
                && MessageDigest.isEqual(cached.passwordHash, passwordHash)) {
            return token(cached.user);
        }

        FineractClient.AuthenticatedUser fineractUser;
        try {
            fineractUser = fineract.authenticate(username, password)
                    .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        } catch (FineractException e) {
            throw new AuthenticationServiceException("Cannot reach Fineract to log in", e);
        }

        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String fineractRole : fineractUser.roles()) {
            Role role = roleMapping.get(fineractRole.toLowerCase());
            if (role != null) {
                roles.addAll(role.withLowerRoles());
            }
        }
        if (roles.isEmpty()) {
            throw new DisabledException("User has no asset financing role in Fineract");
        }

        AppUser user = new AppUser(fineractUser.username(), fineractUser.officeId(), fineractUser.staffId(), roles);
        cache.put(username, new CachedLogin(passwordHash, user, clock.instant().plus(CACHE_TTL)));
        return token(user);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    static UsernamePasswordAuthenticationToken token(AppUser user) {
        return UsernamePasswordAuthenticationToken.authenticated(user, null,
                user.roles().stream().map(r -> new SimpleGrantedAuthority(r.authority())).toList());
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record CachedLogin(byte[] passwordHash, AppUser user, Instant expires) {
    }
}
