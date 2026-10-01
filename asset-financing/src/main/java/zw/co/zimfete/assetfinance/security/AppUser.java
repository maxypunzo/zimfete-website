package zw.co.zimfete.assetfinance.security;

import java.io.Serializable;
import java.util.Set;

/** The logged-in user, taken from their Fineract account. */
public record AppUser(String username, long officeId, Long staffId, Set<Role> roles) implements Serializable {

    public boolean has(Role role) {
        return roles.contains(role);
    }
}
