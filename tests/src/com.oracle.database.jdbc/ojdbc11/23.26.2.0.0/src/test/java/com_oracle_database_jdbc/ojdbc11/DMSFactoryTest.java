/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.driver.DMSFactory;
import org.junit.jupiter.api.Test;

public class DMSFactoryTest {
    @Test
    void createsTheConfiguredMonitoringFactory() {
        String propertyName = "oracle.jdbc.diagnostic.enableObservability";
        String previousValue = System.setProperty(propertyName, "true");
        try {
            DMSFactory factory = DMSFactory.getInstance();

            assertThat(factory).isNotNull();
            assertThat(DMSFactory.isDMSEnabled()).isTrue();
            assertThat(DMSFactory.getDMSVersion()).isNotNull();
            assertThat(factory.createNoun("database", "test")).isNotNull();
        } finally {
            if (previousValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previousValue);
            }
        }
    }
}
