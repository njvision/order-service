package com.mdbookshop.orderservice.order.web;

import com.mdbookshop.orderservice.order.domain.Order;
import com.mdbookshop.orderservice.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
public class OrderJsonTests {

    @Autowired
    private JacksonTester<Order> json;

    @Test
    void testSerialize() throws IOException {
        Instant now = Instant.now();
        var order = new Order(394L, "1234567890", "Book Name", 9.90, 1,
                OrderStatus.ACCEPTED, now, now, null,null, 21);

        JsonContent<Order> writeOrder = json.write(order);

        assertThat(writeOrder).extractingJsonPathNumberValue("@.id").isEqualTo(order.id().intValue());
        assertThat(writeOrder).extractingJsonPathStringValue("@.bookIsbn").isEqualTo(order.bookIsbn());
        assertThat(writeOrder).extractingJsonPathStringValue("@.bookName").isEqualTo(order.bookName());
        assertThat(writeOrder).extractingJsonPathNumberValue("@.bookPrice").isEqualTo(order.bookPrice());
        assertThat(writeOrder).extractingJsonPathNumberValue("@.quantity").isEqualTo(order.quantity());
        assertThat(writeOrder).extractingJsonPathStringValue("@.status").isEqualTo(order.status().toString());
        assertThat(writeOrder).extractingJsonPathStringValue("@.createdDate").isEqualTo(order.createdDate().toString());
        assertThat(writeOrder).extractingJsonPathStringValue("@.lastModifiedDate").isEqualTo(order.lastModifiedDate().toString());
        assertThat(writeOrder).extractingJsonPathNumberValue("@.version").isEqualTo(order.version());
    }
}
