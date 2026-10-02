package zw.co.zimfete.assetfinance.reference;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import zw.co.zimfete.assetfinance.fineract.FineractClient;
import zw.co.zimfete.assetfinance.fineract.FineractClient.ClientSummary;
import zw.co.zimfete.assetfinance.fineract.FineractClient.Office;
import zw.co.zimfete.assetfinance.security.AppUser;
import zw.co.zimfete.assetfinance.security.OfficeAccess;
import zw.co.zimfete.assetfinance.web.BusinessRuleException;

/** Lookups from Fineract that the staff screens need. */
@RestController
public class ReferenceController {

    private final FineractClient fineract;
    private final OfficeAccess officeAccess;

    public ReferenceController(FineractClient fineract, OfficeAccess officeAccess) {
        this.fineract = fineract;
        this.officeAccess = officeAccess;
    }

    @GetMapping("/api/offices")
    public List<Office> offices() {
        return fineract.listOffices();
    }

    /** Member search; officers only get members of their own location. */
    @GetMapping("/api/members")
    public List<ClientSummary> members(@RequestParam String query, @AuthenticationPrincipal AppUser user) {
        String q = query.trim();
        if (q.length() < 2) {
            throw new BusinessRuleException("Type at least 2 characters to search");
        }
        boolean all = officeAccess.seesAllOffices(user);
        return fineract.searchClients(q).stream()
                .filter(c -> all || c.officeId() == user.officeId())
                .limit(25)
                .toList();
    }
}
