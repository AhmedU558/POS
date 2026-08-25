package com.pos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class PosApplicationTests extends AbstractIntegrationTest {

    @Autowired private ApplicationContext context;

    @Test
    void contextLoadsWithSecurityAndPersistenceFoundations() {
        assertThat(context.containsBean("securityFilterChain")).isTrue();
        assertThat(context.containsBean("corsConfigurationSource")).isTrue();
        assertThat(context.containsBean("passwordEncoder")).isTrue();
        assertThat(context.containsBean("flyway")).isTrue();
        assertThat(context.containsBean("posOpenApi")).isTrue();
    }
}
