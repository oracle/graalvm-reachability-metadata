/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.config.AbstractConfigTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class OrgApacheKafkaCommonConfigAbstractConfigTestAnonymous1RestrictedClassLoaderTest {

    @Test
    void testRestrictedClassLoaderFallsBackToKafkaTestClassLoader() {
        Assumptions.assumeFalse(
                isNativeImageRuntime(),
                "Restricted ClassLoader CLASS parsing semantics differ in native-image runtime");

        AbstractConfigTest abstractConfigTest = new AbstractConfigTest();
        abstractConfigTest.setup();
        try {
            abstractConfigTest.testClassConfigs();
        } finally {
            abstractConfigTest.teardown();
        }
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
