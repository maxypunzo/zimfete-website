package zw.co.zimfete.assetfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** The staff web app's login: session cookie, CSRF protection, and no browser password pop-up. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
// The csrf() test helper replaces the cookie token store for the whole context, so each test gets its own.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthSessionTest {

    private static final String ITEM = """
            {"code":"X1","name":"X","category":"OTHER","standardCost":10,"currency":"USD"}""";

    @Autowired
    WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void unauthenticatedGets401WithoutBasicChallenge() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void csrfEndpointIssuesReadableToken() throws Exception {
        var cookie = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void sessionLoginWorksAndStateChangesNeedCsrf() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"officer\",\"password\":\"officer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("officer"))
                .andExpect(jsonPath("$.officeId").value(2))
                .andExpect(jsonPath("$.allOffices").value(false))
                .andExpect(jsonPath("$.roles[0]").value("OFFICER"))
                .andReturn().getRequest().getSession(false);

        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("officer"));

        // Logged in as officer: a POST without the CSRF token is refused before the role check.
        mvc.perform(post("/api/catalogue/items").session(session).contentType(MediaType.APPLICATION_JSON).content(ITEM))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanChangeDataWithSessionAndCsrf() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);

        mvc.perform(post("/api/catalogue/items").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ITEM))
                .andExpect(status().isCreated());
    }
}
