package com.mdbookshop.orderservice;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mdbookshop.orderservice.book.Book;
import com.mdbookshop.orderservice.book.BookClient;
import com.mdbookshop.orderservice.order.domain.Order;
import com.mdbookshop.orderservice.order.domain.OrderStatus;
import com.mdbookshop.orderservice.order.event.OrderAcceptedMessage;
import com.mdbookshop.orderservice.order.web.OrderRequest;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestChannelBinderConfiguration.class)
@Testcontainers
class OrderServiceApplicationTests {

    private static KeycloakToken bjornTokens;

    private static KeycloakToken isabelleTokens;

    @Container
    private static final KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:19.0")
            .withRealmImportFile("test-realm-config.json");

    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutputDestination output;

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private BookClient bookClient;

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", OrderServiceApplicationTests::r2dbcUrl);
        registry.add("spring.r2dbc.username", postgreSQLContainer::getUsername);
        registry.add("spring.r2dbc.password", postgreSQLContainer::getPassword);
        registry.add("spring.flyway.url", postgreSQLContainer::getJdbcUrl);

        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> keycloakContainer.getAuthServerUrl() + "realms/MdBookshop");
    }

    private static String r2dbcUrl() {
        return String.format("r2dbc:postgresql://%s:%s/%s", postgreSQLContainer.getHost(),
                postgreSQLContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT), postgreSQLContainer.getDatabaseName());
    }

    @BeforeAll
    static void generateAccessTokens() {
        WebClient webClient = WebClient.builder()
                .baseUrl(keycloakContainer.getAuthServerUrl() + "realms/MdBookshop/protocol/openid-connect/token")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build();

        isabelleTokens = authenticateWith("isabelle", "password", webClient);
        bjornTokens = authenticateWith("bjorn", "password", webClient);
    }

    @Test
    void whenGetOwnOrdersThenReturn() throws IOException {
        var bookIsbn = "1234567890";
        var book = new Book(bookIsbn, "Title", "Author", 5.50);
        given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(book));

        OrderRequest orderRequest = new OrderRequest(bookIsbn, 1);

        Order expectedOrder = webTestClient
                .post()
                .uri("/orders")
                .headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(Order.class).returnResult().getResponseBody();

        assertThat(expectedOrder).isNotNull();
        assertThat(objectMapper.readValue(output.receive().getPayload(), OrderAcceptedMessage.class))
                .isEqualTo(new OrderAcceptedMessage(expectedOrder.id()));

        webTestClient
                .get()
                .uri("/orders")
                .headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBodyList(Order.class).value(orders -> {
                    List<Long> orderIds = orders.stream().map(Order::id).collect(Collectors.toList());
                    assertThat(orderIds).contains(expectedOrder.id());
                });
    }

    @Test
    void whenGetOrdersForAnotherUserThenNotReturned() throws IOException {
        var bookIsbn = "1234567890";
        var book = new Book(bookIsbn, "Title", "Author", 5.50);
        given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(book));

        OrderRequest orderRequest = new OrderRequest(bookIsbn, 1);

        Order orderBjorn = webTestClient
                .post()
                .uri("/orders")
                .headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(Order.class).returnResult().getResponseBody();

        assertThat(orderBjorn).isNotNull();
        assertThat(objectMapper.readValue(output.receive().getPayload(), OrderAcceptedMessage.class))
                .isEqualTo(new OrderAcceptedMessage(orderBjorn.id()));

        Order orderIsabelle = webTestClient
                .post()
                .uri("/orders")
                .headers(headers -> headers.setBearerAuth(isabelleTokens.accessToken()))
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(Order.class).returnResult().getResponseBody();

        assertThat(orderIsabelle).isNotNull();
        assertThat(objectMapper.readValue(output.receive().getPayload(), OrderAcceptedMessage.class))
                .isEqualTo(new OrderAcceptedMessage(orderIsabelle.id()));

        webTestClient
                .get()
                .uri("/orders")
                .headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBodyList(Order.class).value(orders -> {
                    List<Long> orderIds = orders.stream().map(Order::id).collect(Collectors.toList());
                    assertThat(orderIds).contains(orderBjorn.id());
                    assertThat(orderIds).doesNotContain(orderIsabelle.id());
                });
    }

    @Test
    void whenPostRequestAndBookExistsThenOrderAccepted() throws IOException {
        var bookIsbn = "1234567890";
        var book = new Book(bookIsbn, "Title", "Author", 5.50);
        given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(book));

        OrderRequest orderRequest = new OrderRequest(bookIsbn, 1);
        Order createdOrder = webTestClient
                .post()
                .uri("/orders")
                .headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(Order.class)
                .value(order -> {
                    assertThat(order.bookIsbn()).isEqualTo(orderRequest.isbn());
                    assertThat(order.quantity()).isEqualTo(orderRequest.quantity());
                    assertThat(order.bookName()).isEqualTo(book.title() + "-" + book.author());
                    assertThat(order.bookPrice()).isEqualTo(book.price());
                    assertThat(order.status()).isEqualTo(OrderStatus.ACCEPTED);
                })
                .returnResult().getResponseBody();

        assertThat(objectMapper.readValue(output.receive().getPayload(), OrderAcceptedMessage.class))
                .isEqualTo(new OrderAcceptedMessage(createdOrder.id()));
    }

    @Test
    void whenPostRequestAndBookNotExistsThenOrderRejected() {
        var bookIsbn = "1234567890";
        given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.empty());

        OrderRequest orderRequest = new OrderRequest(bookIsbn, 1);

        WebTestClient.BodySpec<Order, ?> rejectedOrder = webTestClient
                .post()
                .uri("/orders")
                .headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(Order.class)
                .value(order -> {
                    assertThat(order.bookIsbn()).isEqualTo(orderRequest.isbn());
                    assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
                    assertThat(order.quantity()).isEqualTo(orderRequest.quantity());
                });
    }

    private static KeycloakToken authenticateWith(String username, String password, WebClient webClient) {
        return webClient
                .post()
                .body(BodyInserters.fromFormData("grant_type", "password")
                        .with("client_id", "md-test")
                        .with("username", username)
                        .with("password", password)
                )
                .retrieve()
                .bodyToMono(KeycloakToken.class)
                .block();
    }

    private record KeycloakToken(String accessToken) {

        @JsonCreator
        private KeycloakToken(@JsonProperty("access_token") final String accessToken) {
            this.accessToken = accessToken;
        }
    }
}
