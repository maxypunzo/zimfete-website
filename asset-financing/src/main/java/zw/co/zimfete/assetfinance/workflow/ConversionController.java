package zw.co.zimfete.assetfinance.workflow;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import zw.co.zimfete.assetfinance.application.ApplicationView;
import zw.co.zimfete.assetfinance.security.AppUser;

@RestController
public class ConversionController {

    private final ConversionService conversion;

    public ConversionController(ConversionService conversion) {
        this.conversion = conversion;
    }

    /** Safe to call again if it failed part-way: it resumes where it stopped. */
    @PostMapping("/api/applications/{id}/convert")
    @PreAuthorize("hasRole('MANAGER')")
    public ApplicationView convert(@PathVariable long id, @AuthenticationPrincipal AppUser user) {
        return ApplicationView.of(conversion.convert(id, user));
    }
}
