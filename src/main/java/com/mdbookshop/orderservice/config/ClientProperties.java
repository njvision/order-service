package com.mdbookshop.orderservice.config;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.validation.constraints.NotNull;
import java.net.URI;

@ConfigurationProperties(prefix = "md")
public record ClientProperties(
        @NotNull
       URI catalogServiceUri
) { }
