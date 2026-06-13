package com.ragdocs.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void issuesAndParsesToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-at-least-32-bytes-long");
        properties.setExpirationMinutes(30);
        JwtService service = new JwtService(properties, new ObjectMapper());

        String token = service.issue(new CurrentUser(1L, "admin", "ADMIN"));
        CurrentUser parsed = service.parse(token);

        assertThat(parsed.id()).isEqualTo(1L);
        assertThat(parsed.username()).isEqualTo("admin");
        assertThat(parsed.role()).isEqualTo("ADMIN");
    }
}
