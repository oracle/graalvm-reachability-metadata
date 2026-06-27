/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.config.AbstractConfigTest;
import org.junit.jupiter.api.Test;

public class OrgApacheKafkaCommonConfigAbstractConfigTestAnonymous1RestrictedClassLoaderTest {

    @Test
    void resolvesClassConfigWithRestrictedContextClassLoader() {
        AbstractConfigTest abstractConfigTest = new AbstractConfigTest();
        abstractConfigTest.setup();
        try {
            abstractConfigTest.testClassConfigs();
        } finally {
            abstractConfigTest.teardown();
        }
    }
}
