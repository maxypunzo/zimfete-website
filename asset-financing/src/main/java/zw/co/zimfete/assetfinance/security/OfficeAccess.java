package zw.co.zimfete.assetfinance.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import zw.co.zimfete.assetfinance.config.ZimfeteProperties;

/** Officers only see members of their own location; Head Office users see every location. */
@Component
public class OfficeAccess {

    private final long headOfficeId;

    public OfficeAccess(ZimfeteProperties properties) {
        this.headOfficeId = properties.policy().headOfficeId();
    }

    public boolean seesAllOffices(AppUser user) {
        return user.officeId() == headOfficeId;
    }

    public void check(AppUser user, long officeId) {
        if (!seesAllOffices(user) && user.officeId() != officeId) {
            throw new AccessDeniedException("This member belongs to another location");
        }
    }

    /** Office filter for list queries: null means "all offices". */
    public Long scope(AppUser user, Long requestedOfficeId) {
        if (seesAllOffices(user)) {
            return requestedOfficeId;
        }
        return user.officeId();
    }
}
