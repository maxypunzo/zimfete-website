package zw.co.zimfete.assetfinance.queue;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import zw.co.zimfete.assetfinance.security.AppUser;
import zw.co.zimfete.assetfinance.security.OfficeAccess;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queue;
    private final OfficeAccess officeAccess;

    public QueueController(QueueService queue, OfficeAccess officeAccess) {
        this.queue = queue;
        this.officeAccess = officeAccess;
    }

    @GetMapping
    public List<QueueService.QueueEntry> queue(@RequestParam(required = false) Long officeId,
                                               @AuthenticationPrincipal AppUser user) {
        return queue.queue(officeAccess.scope(user, officeId));
    }

    @GetMapping("/forecast")
    public QueueService.Forecast forecast(@RequestParam(defaultValue = "60") @Min(1) @Max(365) int days,
                                          @RequestParam(required = false) Long officeId,
                                          @AuthenticationPrincipal AppUser user) {
        return queue.forecast(days, officeAccess.scope(user, officeId));
    }
}
