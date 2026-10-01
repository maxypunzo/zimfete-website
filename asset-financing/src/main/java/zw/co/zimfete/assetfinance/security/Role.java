package zw.co.zimfete.assetfinance.security;

import java.util.EnumSet;
import java.util.Set;

/**
 * Module roles. Each role includes the ones below it: an ADMIN can do everything a MANAGER can,
 * and a MANAGER everything an OFFICER can.
 */
public enum Role {
    /** Asset finance officer at a location: opens applications, records deposits' progress, deliveries. */
    OFFICER,
    /** Approves purchase orders, converts delivered assets into loans, repossesses. */
    MANAGER,
    /** Maintains the asset catalogue and suppliers. */
    ADMIN;

    public Set<Role> withLowerRoles() {
        return EnumSet.range(OFFICER, this);
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
