/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.SpringProperties;

/** Verifies Spring's local property registry. */
public class SpringPropertiesTest {
    @Test
    void storesAndReadsLocalProperty() {
        SpringProperties.setFlag("spring.core.coverage");
        try {
            assertThat(SpringProperties.getProperty("spring.core.coverage")).isEqualTo("true");
            assertThat(SpringProperties.getFlag("spring.core.coverage")).isTrue();
        } finally {
            SpringProperties.setProperty("spring.core.coverage", null);
        }
    }
}
