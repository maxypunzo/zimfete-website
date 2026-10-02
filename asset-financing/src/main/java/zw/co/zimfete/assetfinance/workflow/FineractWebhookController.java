package zw.co.zimfete.assetfinance.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import zw.co.zimfete.assetfinance.application.ApplicationSyncService;
import zw.co.zimfete.assetfinance.config.ZimfeteProperties;

/**
 * Receives Fineract "Web" hooks so progress updates the moment a deposit or repayment is posted.
 * Payload URL in Fineract: {@code http://<this-server>:8090/api/webhooks/fineract/<webhook-token>/}
 * (deploy/setup-fineract.py registers it). Events: SAVINGSACCOUNT DEPOSIT / WITHDRAWAL / UNDOTRANSACTION
 * and LOAN REPAYMENT. The hourly sync catches anything a hook misses.
 *
 * The token is part of the path because Fineract's hook client (Retrofit) needs the URL to end with
 * "/" and drops any query string when it posts. Fineract also sends a GET to the URL when the hook
 * is created, to check it is reachable.
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

    @GetMapping({ "/api/webhooks/fineract/{token}", "/api/webhooks/fineract/{token}/" })
    public ResponseEntity<Void> ping(@PathVariable String token) {
        return validToken(token) ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping({ "/api/webhooks/fineract/{token}", "/api/webhooks/fineract/{token}/" })
    public ResponseEntity<Void> receive(@PathVariable String token,
                                        @RequestHeader(name = "X-Fineract-Entity", required = false) String entity,
                                        @RequestBody(required = false) JsonNode body) {
        if (!validToken(token)) {
            log.warn("Rejected a webhook call with a wrong token");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.debug("Fineract hook {}: {}", entity, body);
        if (body == null) {
            return ResponseEntity.accepted().build();
        }
        try {
            // Fineract 1.15 sends {"entityName":..., "actionName":..., "request":{...}, "response":{"savingsId"/"loanId",
            // "resourceId" (the transaction id)}}; older versions sent the response fields at the top level.
            JsonNode response = body.has("response") ? body.path("response") : body;
            Long savingsId = id(response, "savingsId");
            Long loanId = id(response, "loanId");
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

    private boolean validToken(String given) {
        return this.token.length > 0 && given != null
                && MessageDigest.isEqual(this.token, given.getBytes(StandardCharsets.UTF_8));
    }

    private static Long id(JsonNode body, String field) {
        JsonNode value = body.path(field);
        return value.canConvertToLong() && value.asLong() > 0 ? value.asLong() : null;
    }
}
