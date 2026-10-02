package zw.co.zimfete.assetfinance.security;

import java.util.EnumSet;
import java.util.Map;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Fixed users for local development and automated tests ONLY (zimfete.security.provider=in-memory).
 * The password of each user is the same as the username. Never enable this on a server.
 */
public class DevAuthenticationProvider implements AuthenticationProvider {

    private static final long HEAD_OFFICE = 1;
    private static final long MARONDERA = 2;
    private static final long HWEDZA = 4;

    private static final Map<String, AppUser> USERS = Map.of(
            "officer", new AppUser("officer", MARONDERA, 10L, EnumSet.of(Role.OFFICER)),
            "officer2", new AppUser("officer2", HWEDZA, 11L, EnumSet.of(Role.OFFICER)),
            "manager", new AppUser("manager", HEAD_OFFICE, null, Role.MANAGER.withLowerRoles()),
            "manager2", new AppUser("manager2", HEAD_OFFICE, null, Role.MANAGER.withLowerRoles()),
            "admin", new AppUser("admin", HEAD_OFFICE, null, Role.ADMIN.withLowerRoles()));

    @Override
    public Authentication authenticate(Authentication authentication) {
        AppUser user = USERS.get(authentication.getName());
        if (user == null || !user.username().equals(String.valueOf(authentication.getCredentials()))) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return FineractAuthenticationProvider.token(user);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
