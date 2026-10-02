package zw.co.zimfete.assetfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

/**
 * Walks members through the whole pipeline over the HTTP API, with Fineract replaced by a fake:
 * open → deposit → qualify → queue → purchase order → delivery → conversion → repaid.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AssetFinancingFlowTest {

    private static final long MARONDERA_MEMBER = 101;
    private static final long HWEDZA_MEMBER = 102;
    private static final long MARONDERA_MEMBER_2 = 103;

    @Autowired
    WebApplicationContext context;
    @Autowired
    FakeFineractClient fineract;

    MockMvc mvc;
    long boreholeId;
    long supplierId;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        fineract.addClient(MARONDERA_MEMBER, "Tendai Moyo", 2);
        fineract.addClient(HWEDZA_MEMBER, "Rudo Chikwanha", 4);
        fineract.addClient(MARONDERA_MEMBER_2, "Farai Ncube", 2);

        boreholeId = id(call("admin", post("/api/catalogue/items"), """
                {"code":"BH-40M","name":"Borehole 40m with solar pump","category":"BOREHOLE",
                 "standardCost":4000.00,"currency":"USD"}""", status().isCreated()));
        supplierId = id(call("admin", post("/api/suppliers"), """
                {"name":"Mashonaland Drilling","phone":"+263770000000"}""", status().isCreated()));
    }

    @Test
    void fullLifecycleFromDepositToOwnership() throws Exception {
        // 1. Officer opens an application; the deposit account is opened in Fineract.
        String opened = call("officer", post("/api/applications"),
                "{\"clientId\":" + MARONDERA_MEMBER + ",\"catalogueItemId\":" + boreholeId + "}",
                status().isCreated());
        long appId = id(opened);
        long savingsId = ((Number) JsonPath.read(opened, "$.savingsAccountId")).longValue();
        assertThat((String) JsonPath.read(opened, "$.status")).isEqualTo("SAVING");
        assertThat((Double) JsonPath.read(opened, "$.depositTarget")).isEqualTo(2000.0);

        // 2. Member deposits in Fineract; refresh collects the $10 opening fee and moves them into the queue.
        fineract.deposit(savingsId, "5", LocalDate.of(2026, 7, 20));
        call("officer", post("/api/applications/" + appId + "/refresh"), null, status().isOk(),
                jsonPath("$.application.depositedAmount").value(5.0));
        assertThat(fineract.postings).noneMatch(p -> p.startsWith("pay-fee:")); // not enough to cover the fee yet
        fineract.deposit(savingsId, "1500", LocalDate.of(2026, 8, 1));
        fineract.deposit(savingsId, "600", LocalDate.of(2026, 9, 15));
        call("officer", post("/api/applications/" + appId + "/refresh"), null, status().isOk(),
                jsonPath("$.application.status").value("QUALIFIED"),
                jsonPath("$.application.depositedAmount").value(2095.0),
                jsonPath("$.progress.percentComplete").value(104.7));
        assertThat(fineract.postings).containsOnlyOnce("pay-fee:" + savingsId + ":10.00");

        call("officer", get("/api/queue"), null, status().isOk(),
                jsonPath("$[0].applicationId").value(appId),
                jsonPath("$[0].position").value(1),
                jsonPath("$[0].cumulativeCashNeeded").value(4000.0));
        call("manager", get("/api/queue/forecast?days=60"), null, status().isOk(),
                jsonPath("$.byCurrency.USD.inQueue").value(1),
                jsonPath("$.byCurrency.USD.cashNeededNow").value(4000.0));

        // 3. Maker-checker: a manager cannot approve their own purchase order.
        long ownPo = id(call("manager", post("/api/applications/" + appId + "/purchase-orders"),
                "{\"supplierId\":" + supplierId + "}", status().isCreated()));
        call("manager", post("/api/purchase-orders/" + ownPo + "/approve"), null, status().isConflict());
        call("manager", post("/api/purchase-orders/" + ownPo + "/cancel"), "{\"reason\":\"raised in error\"}",
                status().isOk());

        // Officer raises it; officers cannot approve; a manager does.
        long po = id(call("officer", post("/api/applications/" + appId + "/purchase-orders"),
                "{\"supplierId\":" + supplierId + "}", status().isCreated(), jsonPath("$.amount").value(4000.0)));
        call("officer", post("/api/purchase-orders/" + po + "/approve"), null, status().isForbidden());
        call("manager", post("/api/purchase-orders/" + po + "/approve"), null, status().isOk(),
                jsonPath("$.status").value("APPROVED"));

        // 4. Officer confirms delivery in the field, with GPS and a photo.
        long assetId = id(call("officer", post("/api/applications/" + appId + "/delivery"), """
                {"serialNumber":"PUMP-7781","latitude":-18.1853,"longitude":31.5519,
                 "deliveredOn":"2026-09-30","memberAcknowledged":true}""", status().isCreated(),
                jsonPath("$.ownership").value("SACCO_OWNED")));
        mvc.perform(multipart("/api/assets/" + assetId + "/photos")
                        .file(new MockMultipartFile("file", "site.jpg", "image/jpeg", new byte[] {1, 2, 3}))
                        .with(httpBasic("officer", "officer")))
                .andExpect(status().isCreated());

        // 5. Manager converts: deposit applied + loan for the balance, in Fineract.
        String converted = call("manager", post("/api/applications/" + appId + "/convert"), null, status().isOk(),
                jsonPath("$.status").value("REPAYING"),
                jsonPath("$.depositApplied").value(2095.0),
                jsonPath("$.financedAmount").value(1905.0));
        long loanId = ((Number) JsonPath.read(converted, "$.loanId")).longValue();
        assertThat(fineract.postings).containsSubsequence("withdraw:" + savingsId + ":2095.00",
                "create-loan:" + loanId + ":1905.00", "approve-loan:" + loanId, "disburse-loan:" + loanId + ":1905.00");
        call("manager", get("/api/purchase-orders/" + po), null, status().isOk(),
                jsonPath("$.status").value("COMPLETED"));

        // 6. Loan repaid in Fineract → hook → paid off and ownership transferred.
        fineract.closeLoan(loanId);
        mvc.perform(post("/api/webhooks/fineract/wrong/").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loanId\":" + loanId + "}")).andExpect(status().isForbidden());
        // Fineract checks the URL with a GET when the hook is created.
        mvc.perform(get("/api/webhooks/fineract/test-token/")).andExpect(status().isOk());
        mvc.perform(post("/api/webhooks/fineract/test-token/").header("X-Fineract-Entity", "LOAN")
                .contentType(MediaType.APPLICATION_JSON).content(hookPayload("LOAN", "REPAYMENT",
                        "\"loanId\":" + loanId + ",\"resourceId\":987")))
                .andExpect(status().isAccepted());

        call("officer", get("/api/applications/" + appId), null, status().isOk(),
                jsonPath("$.status").value("PAID_OFF"));
        call("officer", get("/api/assets/" + assetId), null, status().isOk(),
                jsonPath("$.ownership").value("TRANSFERRED_TO_MEMBER"));
    }

    @Test
    void officersOnlySeeTheirOwnLocation() throws Exception {
        call("officer", post("/api/applications"),
                "{\"clientId\":" + HWEDZA_MEMBER + ",\"catalogueItemId\":" + boreholeId + "}", status().isForbidden());

        long hwedzaApp = id(call("officer2", post("/api/applications"),
                "{\"clientId\":" + HWEDZA_MEMBER + ",\"catalogueItemId\":" + boreholeId + "}", status().isCreated()));

        call("officer", get("/api/applications/" + hwedzaApp), null, status().isForbidden());
        call("officer", get("/api/applications"), null, status().isOk(), jsonPath("$.length()").value(0));
        call("manager", get("/api/applications"), null, status().isOk(), jsonPath("$.length()").value(1));
        call("officer", get("/api/members?query=rudo"), null, status().isOk(), jsonPath("$.length()").value(0));
        call("officer2", get("/api/members?query=rudo"), null, status().isOk(),
                jsonPath("$[0].id").value(HWEDZA_MEMBER));
        call("manager", get("/api/members?query=a"), null, status().isConflict());
        call("officer", post("/api/catalogue/items"), """
                {"code":"X","name":"X","category":"OTHER","standardCost":1,"currency":"USD"}""",
                status().isForbidden());
    }

    @Test
    void servingOutOfQueueOrderNeedsAReason() throws Exception {
        long first = qualifiedApplication(MARONDERA_MEMBER, LocalDate.of(2026, 9, 1));
        long second = qualifiedApplication(MARONDERA_MEMBER_2, LocalDate.of(2026, 9, 20));

        call("officer", get("/api/queue"), null, status().isOk(),
                jsonPath("$[0].applicationId").value(first), jsonPath("$[1].applicationId").value(second));

        call("officer", post("/api/applications/" + second + "/purchase-orders"),
                "{\"supplierId\":" + supplierId + "}", status().isConflict());
        call("officer", post("/api/applications/" + second + "/purchase-orders"),
                "{\"supplierId\":" + supplierId + ",\"queueOverrideReason\":\"First member's site not ready\"}",
                status().isCreated(), jsonPath("$.queueOverrideReason").value("First member's site not ready"));
    }

    @Test
    void interruptedConversionResumesWithoutDoublePosting() throws Exception {
        long appId = qualifiedApplication(MARONDERA_MEMBER, LocalDate.of(2026, 9, 1));
        long po = id(call("officer", post("/api/applications/" + appId + "/purchase-orders"),
                "{\"supplierId\":" + supplierId + "}", status().isCreated()));
        call("manager", post("/api/purchase-orders/" + po + "/approve"), null, status().isOk());
        call("officer", post("/api/applications/" + appId + "/delivery"), """
                {"latitude":-18.18,"longitude":31.55,"deliveredOn":"2026-09-30","memberAcknowledged":true}""",
                status().isCreated());

        // Fineract fails right after the withdrawal was posted, and again on loan approval.
        fineract.failOnce("afterWithdraw");
        call("manager", post("/api/applications/" + appId + "/convert"), null, status().isBadGateway());
        fineract.failOnce("approveLoan");
        call("manager", post("/api/applications/" + appId + "/convert"), null, status().isBadGateway(),
                jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("approveLoan")));
        call("manager", post("/api/applications/" + appId + "/convert"), null, status().isOk(),
                jsonPath("$.status").value("REPAYING"));

        assertThat(fineract.postings.stream().filter(p -> p.startsWith("withdraw:"))).hasSize(1);
        assertThat(fineract.postings.stream().filter(p -> p.startsWith("create-loan:"))).hasSize(1);
        assertThat(fineract.postings.stream().filter(p -> p.startsWith("disburse-loan:"))).hasSize(1);

        call("manager", post("/api/applications/" + appId + "/convert"), null, status().isConflict());
    }

    @Test
    void depositHookQualifiesMemberImmediately() throws Exception {
        String opened = call("officer", post("/api/applications"),
                "{\"clientId\":" + MARONDERA_MEMBER + ",\"catalogueItemId\":" + boreholeId + "}", status().isCreated());
        long appId = id(opened);
        long savingsId = ((Number) JsonPath.read(opened, "$.savingsAccountId")).longValue();

        fineract.deposit(savingsId, "2010", LocalDate.of(2026, 10, 1));
        mvc.perform(post("/api/webhooks/fineract/test-token/").header("X-Fineract-Entity", "SAVINGSACCOUNT")
                .contentType(MediaType.APPLICATION_JSON).content(hookPayload("SAVINGSACCOUNT", "DEPOSIT",
                        "\"savingsId\":" + savingsId + ",\"resourceId\":9")))
                .andExpect(status().isAccepted());

        call("officer", get("/api/applications/" + appId), null, status().isOk(),
                jsonPath("$.status").value("QUALIFIED"));
    }

    /** The shape Fineract 1.15 actually posts (captured from a real server). */
    private static String hookPayload(String entity, String action, String responseFields) {
        return """
                {"createdByName":"mifos","request":{"transactionAmount":5.0,"locale":"en"},"clientId":3,
                 "createdBy":1,"officeId":2,"entityName":"%s","response":{"clientId":3,%s,"changes":{}},
                 "createdByFullName":"App Administrator","actionName":"%s","timestamp":"2026-10-02T16:17:53Z"}
                """.formatted(entity, responseFields, action);
    }

    private long qualifiedApplication(long clientId, LocalDate depositDate) throws Exception {
        String opened = call("officer", post("/api/applications"),
                "{\"clientId\":" + clientId + ",\"catalogueItemId\":" + boreholeId + "}", status().isCreated());
        long appId = id(opened);
        fineract.deposit(((Number) JsonPath.read(opened, "$.savingsAccountId")).longValue(), "2010", depositDate);
        call("officer", post("/api/applications/" + appId + "/refresh"), null, status().isOk(),
                jsonPath("$.application.status").value("QUALIFIED"));
        // Make qualification order follow the order of calls in the test.
        Thread.sleep(5);
        return appId;
    }

    private String call(String user, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                        String body, ResultMatcher... expectations) throws Exception {
        req.with(httpBasic(user, user));
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        ResultActions result = mvc.perform(req);
        for (ResultMatcher m : expectations) {
            result.andExpect(m);
        }
        return result.andReturn().getResponse().getContentAsString();
    }

    private static long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
