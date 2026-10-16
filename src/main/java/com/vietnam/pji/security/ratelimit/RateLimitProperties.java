package com.vietnam.pji.security.ratelimit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    private List<Rule> rules = new ArrayList<>();

    @Getter
    @Setter
    public static class Rule {
        private String name;
        private List<String> paths = new ArrayList<>();
        private long capacity; // sức chứa bucket
        private long refillTokens;
        private Duration refillPeriod;
        private boolean perUser = true; // giới hạn theo người dùng
    }
}
