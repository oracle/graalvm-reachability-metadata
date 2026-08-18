/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.utils.JavaTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThatCode;

public class OrgApacheKafkaCommonUtilsJavaTestTest {

    @Test
    @Timeout(60)
    void loadsKerberosLoginModuleSelectedForStandardJdk() {
        String originalVendor = System.getProperty("java.vendor");
        String originalRuntimeName = System.getProperty("java.runtime.name");
        try {
            System.setProperty("java.vendor", "Oracle Corporation");
            System.setProperty("java.runtime.name", "Java(TM) SE Runtime Environment");

            JavaTest javaTest = new JavaTest();

            assertThatCode(javaTest::testLoadKerberosLoginModule).doesNotThrowAnyException();
        } finally {
            restoreSystemProperty("java.vendor", originalVendor);
            restoreSystemProperty("java.runtime.name", originalRuntimeName);
        }
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
