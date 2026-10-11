package com.example.userservice;

import com.example.userservice.model.Client;
import com.example.userservice.model.ContactInformation;
import com.example.userservice.outbox.OutboxWriter;
import com.example.userservice.repository.ClientRepository;
import com.example.userservice.services.ClientUpsertResult;
import com.example.userservice.services.UserService;
import com.example.userservice.services.UserServiceImp;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "user-sync.api-key=" + ClientUpsertIntegrationTest.API_KEY,
        "spring.grpc.server.port=0" // random port: don't collide with a locally running user-service on 9090
})
@AutoConfigureMockMvc
class ClientUpsertIntegrationTest {
    static final String API_KEY = "test-sync-key";
    private static final String SUBKEY = "4f1c2b8e-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private OutboxWriter outboxWriter;

    @BeforeEach
    void cleanDatabase() {
        clientRepository.deleteAll();
        jdbcClient.sql("delete from outbox_event").update();
    }

    @Test
    void creatingAClientWritesOneOutboxEvent() throws Exception {
        mockMvc.perform(sync(API_KEY, body("Ada", "Lovelace", "+31612345678", "Main Street 1")))
                .andExpect(status().isCreated());

        assertThat(outboxEvents()).singleElement().satisfies(event -> {
            assertThat(event.get("aggregate_type")).isEqualTo("client");
            assertThat(event.get("aggregate_id")).isEqualTo(SUBKEY);
            assertThat(event.get("event_type")).isEqualTo("ClientProfileCreated");
            assertThat(event.get("event_id")).isNotNull();
            assertThat(JsonPath.<String>read(event.get("payload").toString(), "$.subKey")).isEqualTo(SUBKEY);
            assertThat(JsonPath.<String>read(event.get("payload").toString(), "$.email")).isEqualTo("ada@example.com");
        });
    }

    @Test
    void newClientGetsSequenceIdAndProfileKeyThatSurviveUpdates() throws Exception {
        mockMvc.perform(sync(API_KEY, body("Ada", "Lovelace", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profileKey").isNotEmpty());
        Client created = clientRepository.findBySubKey(SUBKEY).orElseThrow();
        assertThat(created.getId()).isGreaterThanOrEqualTo(100000L);
        assertThat(created.getProfileKey()).isEqualTo(new UserServiceImp(clientRepository, outboxWriter).hashFunction(created.getId()));

        mockMvc.perform(sync(API_KEY, body("Ada", "King", null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileKey").value(created.getProfileKey()));
        Client updated = clientRepository.findBySubKey(SUBKEY).orElseThrow();
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getProfileKey()).isEqualTo(created.getProfileKey());
    }

    @Test
    void updatingAClientWritesNoOutboxEvent() throws Exception {
        mockMvc.perform(sync(API_KEY, body("Ada", "Lovelace", null, null))).andExpect(status().isCreated());
        mockMvc.perform(sync(API_KEY, body("Ada", "King", null, null))).andExpect(status().isOk());

        assertThat(outboxEvents()).hasSize(1);
    }

    @Test
    void outboxWriterRefusesToRunOutsideATransaction() {
        assertThatThrownBy(() -> outboxWriter.write("client", SUBKEY, "ClientProfileCreated", "{}"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void concurrentUpsertsOfANewUserCreateExactlyOneClient() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ClientUpsertResult>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String lastName = "Lovelace-" + i;
            results.add(pool.submit(() -> {
                start.await();
                return userService.upsertClientProfile(SUBKEY, "Ada", lastName, "ada@example.com", null, null);
            }));
        }

        start.countDown(); // release all threads at once
        int created = 0;
        for (Future<ClientUpsertResult> result : results) {
            if (result.get(30, TimeUnit.SECONDS).created()) {
                created++;
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(clientRepository.count()).isEqualTo(1);
        assertThat(outboxEvents()).hasSize(1);
    }

    @Test
    void rolledBackUpsertLeavesNoClientAndNoEvent() {
        transactionTemplate.executeWithoutResult(status -> {
            userService.upsertClientProfile(SUBKEY, "Ada", "Lovelace", "ada@example.com", null, null);
            status.setRollbackOnly();
        });

        assertThat(clientRepository.count()).isZero();
        assertThat(outboxEvents()).isEmpty();
    }

    private List<Map<String, Object>> outboxEvents() {
        return jdbcClient.sql("select * from outbox_event").query().listOfRows();
    }

    @Test
    void firstSyncCreatesAndRepeatedSyncUpdatesTheSameClient() throws Exception {
        mockMvc.perform(sync(API_KEY, body("Ada", "Lovelace", "+31612345678", "Main Street 1, Enschede")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subkey").value(SUBKEY))
                .andExpect(jsonPath("$.address").value("Main Street 1, Enschede"));

        mockMvc.perform(sync(API_KEY, body("Ada", "King", "+31600000000", "Canal Road 2, Deventer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("King"));

        assertThat(clientRepository.findAll()).singleElement().satisfies(client -> {
            assertThat(client.getSubKey()).isEqualTo(SUBKEY);
            assertThat(client.getSurname()).isEqualTo("King");
            assertThat(client.getContactInformation().phoneNumber()).isEqualTo("+31600000000");
            assertThat(client.getContactInformation().address()).isEqualTo("Canal Road 2, Deventer");
        });
    }

    @Test
    void nullValuesAreAccepted() throws Exception {
        String bodyWithNulls = """
                {"subkey":"%s","firstName":"Ada","lastName":null,"email":null,"phoneNumber":null,"address":null}
                """.formatted(SUBKEY);

        mockMvc.perform(sync(API_KEY, bodyWithNulls)).andExpect(status().isCreated());
        mockMvc.perform(sync(API_KEY, bodyWithNulls))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value(nullValue()))
                .andExpect(jsonPath("$.address").value(nullValue()));
    }

    @Test
    void missingSubkeyIsRejected() throws Exception {
        mockMvc.perform(sync(API_KEY, """
                        {"firstName":"Ada"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingOrWrongApiKeyIsRejected() throws Exception {
        String body = body("Ada", "Lovelace", "+31612345678", "Main Street 1");

        mockMvc.perform(post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(sync("wrong-key", body))
                .andExpect(status().isUnauthorized());

        assertThat(clientRepository.count()).isZero();
    }

    @Test
    void encodedPathCannotBypassApiKey() throws Exception {
        mockMvc.perform(post(URI.create("/api/v1/client%73"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ada", "Lovelace", null, null)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 404));

        assertThat(clientRepository.count()).isZero();
    }

    @Test
    void meReturnsTheProfileOfTheTokenSubject() throws Exception {
        mockMvc.perform(sync(API_KEY, body("Ada", "Lovelace", null, null))).andExpect(status().isCreated());

        // Loaded from the database: the all-null embeddable comes back as null and must still map.
        mockMvc.perform(get("/api/v1/clients/me").with(jwt().jwt(token -> token.subject(SUBKEY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subkey").value(SUBKEY))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.phoneNumber").value(nullValue()))
                .andExpect(jsonPath("$.address").value(nullValue()));
    }

    @Test
    void meWithoutProfileCreatesItFromTheToken() throws Exception {
        mockMvc.perform(get("/api/v1/clients/me").with(jwt().jwt(token -> token.subject(SUBKEY)
                        .claim("given_name", "Ada").claim("family_name", "Lovelace").claim("email", "ada@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subkey").value(SUBKEY))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.email").value("ada@example.com"));

        assertThat(outboxEvents()).singleElement()
                .satisfies(event -> assertThat(event.get("event_type")).isEqualTo("ClientProfileCreated"));
    }

    @Test
    void meRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/clients/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void profilesCannotBeReadBySubkeyInPath() throws Exception {
        mockMvc.perform(sync(API_KEY, body("Ada", "Lovelace", null, null))).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/clients/" + SUBKEY).with(jwt().jwt(token -> token.subject("someone-else"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void databaseRejectsDuplicateProfileKey() {
        clientRepository.saveAndFlush(Client.builder().subKey("sub-a").profileKey("q0U").build());

        assertThatThrownBy(() -> clientRepository.saveAndFlush(Client.builder().subKey("sub-b").profileKey("q0U").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void clientsWithoutProfileKeyDoNotConflict() {
        clientRepository.saveAndFlush(Client.builder().subKey("sub-a").build());
        clientRepository.saveAndFlush(Client.builder().subKey("sub-b").build());

        assertThat(clientRepository.count()).isEqualTo(2);
    }

    @Test
    void databaseRejectsDuplicateSubKey() {
        clientRepository.saveAndFlush(Client.builder().subKey(SUBKEY).contactInformation(new ContactInformation(null, null)).build());

        assertThatThrownBy(() -> clientRepository.saveAndFlush(Client.builder().subKey(SUBKEY).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static org.springframework.test.web.servlet.RequestBuilder sync(String apiKey, String body) {
        return post("/api/v1/clients")
                .header("X-Api-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String body(String firstName, String lastName, String phoneNumber, String address) {
        return """
                {"subkey":"%s","firstName":%s,"lastName":%s,"email":"ada@example.com","phoneNumber":%s,"address":%s}
                """.formatted(SUBKEY, json(firstName), json(lastName), json(phoneNumber), json(address));
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
