package com.example.orderpayment.integration;

import com.example.orderpayment.order.dto.CreateOrderRequest;
import com.example.orderpayment.order.dto.OrderResponse;
import com.example.orderpayment.order.entity.OrderStatus;
import com.example.orderpayment.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration test using Testcontainers.
 *
 * Spins up real Kafka + PostgreSQL containers, boots the full Spring context,
 * and verifies the end-to-end flow:
 *   POST /api/orders → Kafka → PaymentConsumer → DB update → NotificationConsumer
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Integration Tests - Full Order Payment Flow")
@Disabled("Skipped in CI - Testcontainers requires Docker socket")
class OrderPaymentIntegrationTest {

    // ---- Testcontainers ----

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("orderpayment_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    // ---- Tests ----

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("POST /api/orders - should create order and return 201")
    void createOrder_returns201() throws Exception {
        CreateOrderRequest request = buildRequest("CUST-INT-001", UUID.randomUUID().toString());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value("CUST-INT-001"))
                .andExpect(jsonPath("$.status").value("PAYMENT_PROCESSING"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("POST /api/orders - should reject duplicate idempotency key with 409")
    void createOrder_duplicateIdempotencyKey_returns409() throws Exception {
        String idempotencyKey = "dup-key-" + UUID.randomUUID();
        CreateOrderRequest request = buildRequest("CUST-DUP-001", idempotencyKey);

        // First request — should succeed
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second request with same key — should fail
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("POST /api/orders - should reject invalid request with 400")
    void createOrder_invalidRequest_returns400() throws Exception {
        CreateOrderRequest invalidRequest = new CreateOrderRequest();
        // Missing all required fields

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("GET /api/orders/{id} - order status should be updated async by Kafka consumer")
    void getOrder_afterKafkaProcessing_statusUpdated() throws Exception {
        CreateOrderRequest request = buildRequest("CUST-ASYNC-001", UUID.randomUUID().toString());

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderResponse created = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);
        String orderId = created.getId().toString();

        // Wait up to 15 seconds for async Kafka processing to complete
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            MvcResult getResult = mockMvc.perform(get("/api/orders/" + orderId))
                    .andExpect(status().isOk())
                    .andReturn();

            OrderResponse current = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), OrderResponse.class);

            // After Kafka processing, status must be SUCCESS or FAILED (never PROCESSING)
            assertThat(current.getStatus())
                    .isIn(OrderStatus.PAYMENT_SUCCESS, OrderStatus.PAYMENT_FAILED);
        });
    }

    @Test
    @DisplayName("GET /api/orders - should return 401 when unauthenticated")
    void getAllOrders_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("GET /api/orders - should return 403 for non-admin user")
    void getAllOrders_userRole_returns403() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("GET /api/orders - should return 200 for admin user")
    void getAllOrders_adminRole_returns200() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /actuator/health - should be accessible without auth")
    void actuatorHealth_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // ---- Helper ----

    private CreateOrderRequest buildRequest(String customerId, String idempotencyKey) {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerId(customerId);
        req.setCustomerEmail("test@example.com");
        req.setProductName("Test Product");
        req.setQuantity(1);
        req.setAmount(new BigDecimal("99.99"));
        req.setIdempotencyKey(idempotencyKey);
        return req;
    }
}
