package com.mdbookshop.orderservice;

import com.mdbookshop.orderservice.book.Book;
import com.mdbookshop.orderservice.book.BookClient;
import com.mdbookshop.orderservice.order.domain.Order;
import com.mdbookshop.orderservice.order.domain.OrderStatus;
import com.mdbookshop.orderservice.order.web.OrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderServiceApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

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
    }

    private static String r2dbcUrl() {
        return String.format("r2dbc:postgresql://%s:%s/%s", postgreSQLContainer.getHost(),
                postgreSQLContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT), postgreSQLContainer.getDatabaseName());
    }

    @Test
    void whenGetOrdersThenReturn() {
        var bookIsbn = "1234567890";
        var book = new Book(bookIsbn, "Title", "Author", 5.50);
        given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(book));

        OrderRequest orderRequest = new OrderRequest(bookIsbn, 1);
        Order expectedOrder = webTestClient
                .post()
                .uri("/orders")
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(Order.class).returnResult().getResponseBody();

        assertThat(expectedOrder).isNotNull();

        webTestClient
                .get()
                .uri("/orders")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBodyList(Order.class).value(orders ->
                        assertThat(orders.stream().filter(order -> order.bookIsbn().equals(bookIsbn)).findAny()).isNotEmpty());
    }

    @Test
    void whenPostRequestAndBookExistsThenOrderAccepted() {
        var bookIsbn = "1234567890";
        var book = new Book(bookIsbn, "Title", "Author", 5.50);
        given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(book));

        OrderRequest orderRequest = new OrderRequest(bookIsbn, 1);
        Order createdOrder = webTestClient
                .post()
                .uri("/orders")
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(Order.class).returnResult().getResponseBody();

        assertThat(createdOrder).isNotNull();
        assertThat(createdOrder.bookIsbn()).isEqualTo(orderRequest.isbn());
        assertThat(createdOrder.quantity()).isEqualTo(orderRequest.quantity());
        assertThat(createdOrder.bookName()).isEqualTo(book.title() + "-" + book.author());
        assertThat(createdOrder.bookPrice()).isEqualTo(book.price());
        assertThat(createdOrder.status()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void  whenPostRequestAndBookNotExistsThenOrderRejected() {
        var bookIsbn = "1234567890";
        given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.empty());

        OrderRequest orderRequest = new OrderRequest(bookIsbn, 1);

        Order rejectedOrder = webTestClient
                .post()
                .uri("/orders")
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(Order.class).returnResult().getResponseBody();

        assertThat(rejectedOrder).isNotNull();
        assertThat(rejectedOrder.bookIsbn()).isEqualTo(orderRequest.isbn());
        assertThat(rejectedOrder.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(rejectedOrder.quantity()).isEqualTo(orderRequest.quantity());
    }
}
