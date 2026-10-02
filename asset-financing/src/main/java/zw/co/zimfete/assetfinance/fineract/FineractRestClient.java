package zw.co.zimfete.assetfinance.fineract;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import zw.co.zimfete.assetfinance.config.ZimfeteProperties;

/**
 * Talks to the Fineract REST API (/fineract-provider/api/v1) using a service account.
 * Dates are sent in Fineract's "dd MMMM yyyy" format and returned as [yyyy, MM, dd] arrays.
 */
public class FineractRestClient implements FineractClient {

    private static final String DATE_FORMAT = "dd MMMM yyyy";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.ENGLISH);
    private static final String TENANT_HEADER = "Fineract-Platform-TenantId";

    private final RestClient rest;
    private final RestClient anonymous;
    private final ZimfeteProperties.Fineract config;

    public FineractRestClient(RestClient.Builder builder, ZimfeteProperties.Fineract config) {
        this.config = config;
        RestClient.Builder base = builder.baseUrl(config.baseUrl())
                .defaultHeader(TENANT_HEADER, config.tenantId())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
        this.anonymous = base.clone().build();
        String token = Base64.getEncoder().encodeToString(
                (config.username() + ":" + config.password()).getBytes(StandardCharsets.UTF_8));
        this.rest = base.defaultHeader("Authorization", "Basic " + token).build();
    }

    @Override
    public ClientInfo getClient(long clientId) {
        JsonNode node = get("/clients/{id}", clientId);
        return new ClientInfo(node.path("id").asLong(), node.path("displayName").asString(),
                node.path("officeId").asLong(), optionalLong(node, "staffId"), node.path("active").asBoolean());
    }

    @Override
    public List<Office> listOffices() {
        List<Office> offices = new ArrayList<>();
        for (JsonNode o : get("/offices")) {
            offices.add(new Office(o.path("id").asLong(), o.path("name").asString()));
        }
        return offices;
    }

    @Override
    public List<ClientSummary> searchClients(String query) {
        List<ClientSummary> clients = new ArrayList<>();
        for (JsonNode c : get("/search?query={q}&resource=clients&exactMatch=false", query)) {
            JsonNode status = c.path("entityStatus");
            clients.add(new ClientSummary(c.path("entityId").asLong(), c.path("entityName").asString(),
                    c.path("entityAccountNo").asString(), c.path("parentId").asLong(),
                    c.path("parentName").asString(), c.path("entityMobileNo").asString(null),
                    "clientStatusType.active".equals(status.path("code").asString())
                            || "Active".equalsIgnoreCase(status.path("value").asString())));
        }
        return clients;
    }

    @Override
    public long openAssetDepositAccount(long clientId, String externalId, LocalDate date) {
        Map<String, Object> body = dated(date);
        body.put("clientId", clientId);
        body.put("productId", config.assetDepositProductId());
        body.put("externalId", externalId);
        body.put("submittedOnDate", format(date));
        if (config.openingFeeChargeId() != null) {
            // Fineract does not copy a product's charges onto accounts opened through the API, so the
            // opening fee is attached here. It is a "Specified due date" charge: an activation charge
            // would stop Fineract from activating an account that has no money in it yet.
            JsonNode charge = get("/charges/{id}", config.openingFeeChargeId());
            body.put("charges", List.of(Map.of("chargeId", config.openingFeeChargeId(),
                    "amount", decimal(charge.path("amount")), "dueDate", format(date))));
        }
        long id = post("/savingsaccounts", body).path("savingsId").asLong();

        Map<String, Object> approve = dated(date);
        approve.put("approvedOnDate", format(date));
        post("/savingsaccounts/" + id + "?command=approve", approve);

        Map<String, Object> activate = dated(date);
        activate.put("activatedOnDate", format(date));
        post("/savingsaccounts/" + id + "?command=activate", activate);
        return id;
    }

    @Override
    public SavingsAccountInfo getSavingsAccount(long savingsAccountId) {
        JsonNode node = get("/savingsaccounts/{id}?associations=transactions,charges", savingsAccountId);
        List<SavingsTransaction> transactions = new ArrayList<>();
        for (JsonNode tx : node.path("transactions")) {
            JsonNode type = tx.path("transactionType");
            transactions.add(new SavingsTransaction(
                    tx.path("id").asLong(),
                    parseDate(tx.path("date")),
                    decimal(tx.path("amount")),
                    type.path("deposit").asBoolean(),
                    type.path("withdrawal").asBoolean(),
                    tx.path("reversed").asBoolean()));
        }
        List<AccountCharge> charges = new ArrayList<>();
        for (JsonNode c : node.path("charges")) {
            if (c.path("isActive").asBoolean(true)) {
                charges.add(new AccountCharge(c.path("id").asLong(), c.path("chargeId").asLong(),
                        decimal(c.path("amountOutstanding"))));
            }
        }
        return new SavingsAccountInfo(node.path("id").asLong(), node.path("clientId").asLong(),
                node.path("currency").path("code").asString(),
                decimal(node.path("summary").path("accountBalance")), transactions, charges);
    }

    @Override
    public void paySavingsCharge(long savingsAccountId, long accountChargeId, BigDecimal amount, LocalDate date) {
        Map<String, Object> body = dated(date);
        body.put("amount", amount);
        body.put("dueDate", format(date));
        post("/savingsaccounts/" + savingsAccountId + "/charges/" + accountChargeId + "?command=paycharge", body);
    }

    @Override
    public long withdrawToSupplier(long savingsAccountId, BigDecimal amount, LocalDate date, String note) {
        Map<String, Object> body = dated(date);
        body.put("transactionDate", format(date));
        body.put("transactionAmount", amount);
        body.put("paymentTypeId", config.supplierPaymentTypeId());
        body.put("note", note);
        return post("/savingsaccounts/" + savingsAccountId + "/transactions?command=withdrawal", body)
                .path("resourceId").asLong();
    }

    @Override
    public Optional<Long> findLoanByExternalId(String externalId) {
        try {
            JsonNode node = get("/loans/external-id/{externalId}", externalId);
            return Optional.of(node.path("id").asLong());
        } catch (FineractException e) {
            if (e.getCause() instanceof HttpClientErrorException http
                    && http.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public long createAssetLoan(long clientId, BigDecimal principal, String externalId, LocalDate date) {
        // Repayment terms come from the loan product template, so they are managed in Fineract, not here.
        JsonNode t = get("/loans/template?templateType=individual&clientId={c}&productId={p}",
                clientId, config.assetLoanProductId());
        Map<String, Object> body = dated(date);
        body.put("loanType", "individual");
        body.put("clientId", clientId);
        body.put("productId", config.assetLoanProductId());
        body.put("externalId", externalId);
        body.put("principal", principal);
        body.put("numberOfRepayments", t.path("numberOfRepayments").asInt());
        body.put("repaymentEvery", t.path("repaymentEvery").asInt());
        body.put("repaymentFrequencyType", t.path("repaymentFrequencyType").path("id").asInt());
        body.put("loanTermFrequency", t.path("termFrequency").asInt());
        body.put("loanTermFrequencyType", t.path("termPeriodFrequencyType").path("id").asInt());
        body.put("interestRatePerPeriod", decimal(t.path("interestRatePerPeriod")));
        body.put("amortizationType", t.path("amortizationType").path("id").asInt());
        body.put("interestType", t.path("interestType").path("id").asInt());
        body.put("interestCalculationPeriodType", t.path("interestCalculationPeriodType").path("id").asInt());
        body.put("transactionProcessingStrategyCode", t.path("transactionProcessingStrategyCode").asString());
        body.put("expectedDisbursementDate", format(date));
        body.put("submittedOnDate", format(date));
        if (config.assetLoanFundId() != null) {
            body.put("fundId", config.assetLoanFundId());
        }
        return post("/loans", body).path("loanId").asLong();
    }

    @Override
    public void approveLoan(long loanId, LocalDate date) {
        Map<String, Object> body = dated(date);
        body.put("approvedOnDate", format(date));
        body.put("expectedDisbursementDate", format(date));
        post("/loans/" + loanId + "?command=approve", body);
    }

    @Override
    public void disburseLoanToSupplier(long loanId, BigDecimal amount, LocalDate date, String note) {
        Map<String, Object> body = dated(date);
        body.put("actualDisbursementDate", format(date));
        body.put("transactionAmount", amount);
        body.put("paymentTypeId", config.supplierPaymentTypeId());
        body.put("note", note);
        post("/loans/" + loanId + "?command=disburse", body);
    }

    @Override
    public LoanInfo getLoan(long loanId) {
        JsonNode node = get("/loans/{id}", loanId);
        JsonNode status = node.path("status");
        JsonNode summary = node.path("summary");
        return new LoanInfo(node.path("id").asLong(), status.path("value").asString(),
                status.path("pendingApproval").asBoolean(), status.path("waitingForDisbursal").asBoolean(),
                status.path("active").asBoolean(),
                status.path("closedObligationsMet").asBoolean() || status.path("closed").asBoolean(),
                decimal(node.path("principal")),
                decimal(summary.path("totalOutstanding")));
    }

    @Override
    public Optional<AuthenticatedUser> authenticate(String username, String password) {
        try {
            JsonNode node = anonymous.post().uri("/authentication")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", username, "password", password))
                    .retrieve().body(JsonNode.class);
            if (node == null || !node.path("authenticated").asBoolean()) {
                return Optional.empty();
            }
            List<String> roles = new ArrayList<>();
            node.path("roles").forEach(r -> roles.add(r.path("name").asString()));
            return Optional.of(new AuthenticatedUser(node.path("username").asString(),
                    node.path("officeId").asLong(), optionalLong(node, "staffId"), roles));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                return Optional.empty();
            }
            throw new FineractException("Fineract authentication failed: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new FineractException("Fineract is unreachable", e);
        }
    }

    private JsonNode get(String uri, Object... vars) {
        try {
            return rest.get().uri(uri, vars).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new FineractException("Fineract GET " + uri + " failed: " + e.getStatusCode() + " "
                    + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new FineractException("Fineract is unreachable", e);
        }
    }

    private JsonNode post(String uri, Map<String, Object> body) {
        try {
            return rest.post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new FineractException("Fineract POST " + uri + " failed: " + e.getStatusCode() + " "
                    + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new FineractException("Fineract is unreachable", e);
        }
    }

    private static Map<String, Object> dated(LocalDate date) {
        Map<String, Object> body = new HashMap<>();
        body.put("locale", "en");
        body.put("dateFormat", DATE_FORMAT);
        return body;
    }

    private static String format(LocalDate date) {
        return FORMATTER.format(date);
    }

    static LocalDate parseDate(JsonNode array) {
        if (!array.isArray() || array.size() < 3) {
            return null;
        }
        return LocalDate.of(array.get(0).asInt(), array.get(1).asInt(), array.get(2).asInt());
    }

    static BigDecimal decimal(JsonNode node) {
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isString() && !node.asString().isBlank()) {
            return new BigDecimal(node.asString());
        }
        return BigDecimal.ZERO;
    }

    private static Long optionalLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asLong() == 0 ? null : value.asLong();
    }
}
