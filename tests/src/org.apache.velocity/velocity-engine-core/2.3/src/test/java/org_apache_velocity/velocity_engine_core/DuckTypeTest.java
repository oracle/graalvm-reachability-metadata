/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity_engine_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.velocity.util.DuckType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DuckTypeTest {
    @BeforeEach
    void clearDuckTypeCache() {
        DuckType.clearCache();
    }

    @Test
    void asEmptyUsesPublicIsEmptyMethodAndReusesCachedMethod() {
        Optional<String> emptyValue = Optional.empty();
        Optional<String> presentValue = Optional.of("velocity");

        assertThat(DuckType.asEmpty(emptyValue)).isTrue();
        assertThat(DuckType.asEmpty(presentValue)).isFalse();
    }
}
