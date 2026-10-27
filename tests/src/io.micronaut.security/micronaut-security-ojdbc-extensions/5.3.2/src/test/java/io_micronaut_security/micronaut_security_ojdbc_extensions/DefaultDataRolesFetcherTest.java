/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_security.micronaut_security_ojdbc_extensions;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.security.ojdbc.extensions.DefaultDataRolesFetcher;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class DefaultDataRolesFetcherTest {
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void mergesAndDeduplicatesDataRolesFromMultipleSources() {
        Set<String> configuredRoles = Set.of("REPORTING", "AUDITOR");
        Set<String> authenticationRoles = Set.of("AUDITOR", "ANALYST");

        Set<String> mergedRoles = DefaultDataRolesFetcher.mergeDataRoles(configuredRoles, authenticationRoles);

        assertThat(mergedRoles).containsExactlyInAnyOrder("REPORTING", "AUDITOR", "ANALYST");
        assertThat(configuredRoles).containsExactlyInAnyOrder("REPORTING", "AUDITOR");
        assertThat(authenticationRoles).containsExactlyInAnyOrder("AUDITOR", "ANALYST");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void representsAbsentDataRolesAsNull() {
        Set<String> mergedRoles = DefaultDataRolesFetcher.mergeDataRoles(null, Set.of());

        assertThat(mergedRoles).isNull();
    }
}
