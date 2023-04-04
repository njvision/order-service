package com.mdbookshop.orderservice.order.event;

public record OrderAcceptedMessage(
        Long orderId
) { }
