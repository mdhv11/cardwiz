package com.cardwiz.userservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ratelimit")
@Getter
@Setter
public class RateLimitProperties {
    private boolean enabled = true;

    private int authLimit = 10;
    private int authWindowSeconds = 60;

    private int expensiveLimit = 12;
    private int expensiveWindowSeconds = 60;

    private int defaultLimit = 120;
    private int defaultWindowSeconds = 60;
}
