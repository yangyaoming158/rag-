package com.ragdocs.config;

import com.ragdocs.auth.JwtProperties;
import com.ragdocs.service.StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, StorageProperties.class})
public class AppConfiguration {
}
