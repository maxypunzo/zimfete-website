package zw.co.zimfete.assetfinance.fineract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import zw.co.zimfete.assetfinance.config.ZimfeteProperties;

class FineractRestClientTest {

    private static final String BASE = "https://fineract.test/fineract-provider/api/v1";

    private MockRestServiceServer server;
    private FineractRestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FineractRestClient(builder, new ZimfeteProperties.Fineract(BASE, "default", "svc", "secret",
                7, 8, 9, 3L, "token"));
    }

    @Test
    void readsSavingsAccountWithFineractDateArrays() {
        server.expect(requestTo(BASE + "/savingsaccounts/42?associations=transactions"))
                .andExpect(header("Fineract-Platform-TenantId", "default"))
                .andExpect(header("Authorization", "Basic c3ZjOnNlY3JldA=="))
                .andRespond(withSuccess("""
                        {"id":42,"clientId":5,"currency":{"code":"USD"},
                         "summary":{"accountBalance":2150.50},
                         "transactions":[
                           {"id":9,"date":[2026,9,15],"amount":600,"reversed":false,
                            "transactionType":{"deposit":true,"withdrawal":false}},
                           {"id":8,"date":[2026,8,1],"amount":1550.5,"reversed":false,
                            "transactionType":{"deposit":true,"withdrawal":false}}]}
                        """, MediaType.APPLICATION_JSON));

        var account = client.getSavingsAccount(42);

        assertThat(account.balance()).isEqualByComparingTo("2150.50");
        assertThat(account.currency()).isEqualTo("USD");
        assertThat(account.transactions()).hasSize(2);
        assertThat(account.transactions().getFirst().date()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(account.transactions().getFirst().deposit()).isTrue();
        server.verify();
    }

    @Test
    void opensApprovesAndActivatesDepositAccount() {
        server.expect(requestTo(BASE + "/savingsaccounts")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.productId").value(7))
                .andExpect(jsonPath("$.externalId").value("AF-ABC"))
                .andExpect(jsonPath("$.submittedOnDate").value("01 October 2026"))
                .andExpect(jsonPath("$.dateFormat").value("dd MMMM yyyy"))
                .andRespond(withSuccess("{\"savingsId\":77,\"resourceId\":77}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/savingsaccounts/77?command=approve"))
                .andRespond(withSuccess("{\"resourceId\":77}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/savingsaccounts/77?command=activate"))
                .andRespond(withSuccess("{\"resourceId\":77}", MediaType.APPLICATION_JSON));

        assertThat(client.openAssetDepositAccount(5, "AF-ABC", LocalDate.of(2026, 10, 1))).isEqualTo(77);
        server.verify();
    }

    @Test
    void createsLoanUsingProductTemplateTermsAndFund() {
        server.expect(requestTo(BASE + "/loans/template?templateType=individual&clientId=5&productId=8"))
                .andRespond(withSuccess("""
                        {"numberOfRepayments":24,"repaymentEvery":1,"repaymentFrequencyType":{"id":2},
                         "termFrequency":24,"termPeriodFrequencyType":{"id":2},"interestRatePerPeriod":2.5,
                         "amortizationType":{"id":1},"interestType":{"id":0},
                         "interestCalculationPeriodType":{"id":1},
                         "transactionProcessingStrategyCode":"mifos-standard-strategy"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/loans")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.principal").value(1900.00))
                .andExpect(jsonPath("$.numberOfRepayments").value(24))
                .andExpect(jsonPath("$.loanTermFrequencyType").value(2))
                .andExpect(jsonPath("$.fundId").value(3))
                .andExpect(jsonPath("$.externalId").value("AF-ABC"))
                .andRespond(withSuccess("{\"loanId\":501,\"resourceId\":501}", MediaType.APPLICATION_JSON));

        assertThat(client.createAssetLoan(5, new BigDecimal("1900.00"), "AF-ABC", LocalDate.of(2026, 10, 1)))
                .isEqualTo(501);
        server.verify();
    }

    @Test
    void missingLoanByExternalIdIsEmpty() {
        server.expect(requestTo(BASE + "/loans/external-id/AF-NONE"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertThat(client.findLoanByExternalId("AF-NONE")).isEmpty();
    }

    @Test
    void authenticationMapsOfficeAndRoles() {
        server.expect(requestTo(BASE + "/authentication"))
                .andExpect(content().json("{\"username\":\"tendai\",\"password\":\"pw\"}"))
                .andExpect(request -> assertThat(request.getHeaders().get("Authorization")).isNull())
                .andRespond(withSuccess("""
                        {"username":"tendai","authenticated":true,"officeId":2,"staffId":10,
                         "roles":[{"id":4,"name":"Asset Finance Officer"}]}
                        """, MediaType.APPLICATION_JSON));

        var user = client.authenticate("tendai", "pw").orElseThrow();

        assertThat(user.officeId()).isEqualTo(2);
        assertThat(user.staffId()).isEqualTo(10L);
        assertThat(user.roles()).containsExactly("Asset Finance Officer");
    }

    @Test
    void wrongPasswordIsEmpty() {
        server.expect(requestTo(BASE + "/authentication"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertThat(client.authenticate("tendai", "wrong")).isEmpty();
    }
}
