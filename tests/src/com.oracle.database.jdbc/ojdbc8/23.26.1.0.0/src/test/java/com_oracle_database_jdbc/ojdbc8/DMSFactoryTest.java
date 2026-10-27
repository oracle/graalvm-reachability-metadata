/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.driver.DMSFactory;
import org.junit.jupiter.api.Test;

public class DMSFactoryTest {
    @Test
    void createsObservabilityObjectsWhenDiagnosticsAreEnabled() {
        DMSFactory factory = DMSFactory.getInstance();

        assertThat(DMSFactory.isDMSEnabled()).isTrue();
        assertThat(DMSFactory.getExecutionContextForJDBC()).isNotNull();
        assertThat(factory.getRoot()).isNotNull();
        assertThat(factory.createNoun("test-noun", "test-type")).isNotNull();
    }
}
