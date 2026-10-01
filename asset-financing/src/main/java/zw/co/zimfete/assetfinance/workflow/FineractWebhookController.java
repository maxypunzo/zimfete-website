package zw.co.zimfete.assetfinance.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import zw.co.zimfete.assetfinance.application.ApplicationSyncService;
import zw.co.zimfete.assetfinance.config.ZimfeteProperties;

/**
 * Receives Fineract "Web" hooks so progress updates the moment a deposit or repayment is posted.
 * In Mifos: Admin → System → Manage Hooks → Web, payload URL
 * {@code https://<this-server>/api/webhooks/fineract?token=<zimfete.fineract.webhook-token>}, events
 * SAVINGSACCOUNT DEPOSIT / WITHDRAWAL / UNDOTRANSACTION and LOAN REPAYMENT / UNDOTRANSACTION.
 * The hourly sync catches anything a hook misses.
 */
@RestController
public class FineractWebhookController {

    private static final Logger log = LoggerFactory.getLogger(FineractWebhookController.class);

    private final ApplicationSyncService sync;
    private final byte[] token;

    public FineractWebhookController(ApplicationSyncService sync, ZimfeteProperties properties) {
        this.sync = sync;
        String configured = properties.fineract().webhookToken();
        this.token = configured == null ? new byte[0] : configured.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/api/webhooks/fineract")
    public ResponseEntity<Void> receive(@RequestParam(required = false) String token,
                                        @RequestHeader(name = "X-Fineract-Entity", required = false) String entity,
                                        @RequestBody(required = false) JsonNode body) {
        if (this.token.length == 0 || token == null
                || !MessageDigest.isEqual(this.token, token.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (body == null) {
            return ResponseEntity.accepted().build();
        }
        try {
            Long savingsId = id(body, "savingsId");
            Long loanId = id(body, "loanId");
            if ("SAVINGSACCOUNT".equalsIgnoreCase(entity) && savingsId == null) {
                savingsId = id(body, "resourceId");
            }
            if ("LOAN".equalsIgnoreCase(entity) && loanId == null) {
                loanId = id(body, "resourceId");
            }
            if (savingsId != null) {
                sync.syncBySavingsAccount(savingsId);
            }
            if (loanId != null) {
                sync.syncByLoan(loanId);
            }
        } catch (RuntimeException e) {
            // Never make Fineract retry endlessly; the scheduled sync will catch up.
            log.warn("Webhook sync failed ({}): {}", entity, e.getMessage());
        }
        return ResponseEntity.accepted().build();
    }

    private static Long id(JsonNode body, String field) {
        JsonNode value = body.path(field);
        return value.canConvertToLong() && value.asLong() > 0 ? value.asLong() : null;
    }
}
