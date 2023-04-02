package com.mdbookshop.orderservice.order.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderRequest(
    @NotBlank(message = "The book ISBN must be defined.")
    String isbn,

    @NotNull(message = "The book quantity must be defined.")
    @Min(value = 1, message = "You must order at least 1 item.")
    @Max(value = 1, message = "You must order more than 5 item.")
    Integer quantity
) { }
