package com.mdbookshop.orderservice.order.event;

public record OrderDispatchedMessage(
        Long orderId
) { }
