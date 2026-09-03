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

/** Verifies initialization from the conventional classpath properties resource. */
public class SpringPropertiesTest {
    @Test
    void readsPropertyLoadedDuringClassInitialization() {
        assertThat(SpringProperties.getProperty("spring.test.dynamic-access")).isEqualTo("enabled");
    }
}
