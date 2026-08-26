package com.pos.auth;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.PasswordRotationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import java.util.List;

import jakarta.servlet.Filter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins where the rotation check actually runs.
 *
 * <p>These exist because of a defect the mutation matrix exposed: deleting the {@code
 * addFilterBefore} line in {@link com.pos.common.security.SecurityConfig} broke no test. Spring
 * Boot auto-registers every {@code Filter} bean with the servlet container, so the filter was
 * living in two places at once — the declared position, and a second one after the entire security
 * chain. {@code OncePerRequestFilter} deduplicates, so the duplicate silently stood in for the
 * declared position and the ADR-013 ordering was never load-bearing.
 *
 * <p>Behavioural tests cannot see this: both positions produce the same 403. Only the wiring can.
 */
class PasswordRotationFilterWiringTests extends AbstractIntegrationTest {

    @Autowired private ApplicationContext context;
    @Autowired private FilterChainProxy filterChainProxy;

    @Test
    void theFilterRunsExactlyOnceInTheSecurityChain() {
        assertThat(rotationFilterPositions()).hasSize(1);
    }

    @Test
    void theFilterSitsImmediatelyBeforeTheAuthorizationDecision() {
        // ADR-013: after authentication has been established, before the permission decision.
        // Rotation is not an authentication outcome -- failing the login itself would strand the
        // holder with no route to fix their own account.
        List<Filter> filters = securityFilters();
        int rotation = rotationFilterPositions().get(0);
        int authorization = -1;
        for (int i = 0; i < filters.size(); i++) {
            if (filters.get(i) instanceof AuthorizationFilter) {
                authorization = i;
            }
        }

        assertThat(authorization).isNotEqualTo(-1);
        assertThat(rotation).isEqualTo(authorization - 1);
    }

    @Test
    void theFilterIsNotAlsoRegisteredWithTheServletContainer() {
        // A second, undeclared registration would substitute for the one above without any
        // behavioural symptom, which is precisely how the ordering guarantee went untested.
        FilterRegistrationBean<?> registration =
                context.getBean(
                        "passwordRotationFilterRegistration", FilterRegistrationBean.class);

        assertThat(registration.isEnabled()).isFalse();
        assertThat(registration.getFilter()).isInstanceOf(PasswordRotationFilter.class);
    }

    private List<Filter> securityFilters() {
        List<SecurityFilterChain> chains = filterChainProxy.getFilterChains();
        assertThat(chains).hasSize(1);
        return chains.get(0).getFilters();
    }

    private List<Integer> rotationFilterPositions() {
        List<Filter> filters = securityFilters();
        return java.util.stream.IntStream.range(0, filters.size())
                .filter(i -> filters.get(i) instanceof PasswordRotationFilter)
                .boxed()
                .toList();
    }
}
