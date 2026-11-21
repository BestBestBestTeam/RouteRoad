package com.routeroad.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class RealClockConfiguration {
    @Bean
    Clock realClock() {
        return Clock.systemUTC();
    }
}
