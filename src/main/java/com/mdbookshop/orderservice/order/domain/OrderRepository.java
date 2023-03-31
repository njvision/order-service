package com.mdbookshop.orderservice.order.domain;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

public class OrderRepository extends ReactiveCrudRepository<Order, Long> {
}
