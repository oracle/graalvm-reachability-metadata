/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.utils.JavaTest;
import org.junit.jupiter.api.Test;

public class OrgApacheKafkaCommonUtilsJavaTestTest {

    @Test
    void loadsKerberosLoginModuleThroughKafkaJavaUtilityTestPath() throws Exception {
        JavaTest javaTest = new JavaTest();
        javaTest.before();
        try {
            System.setProperty("java.vendor", "Oracle Corporation");
            System.setProperty("java.runtime.name", "OpenJDK Runtime Environment");

            javaTest.testLoadKerberosLoginModule();
        } finally {
            javaTest.after();
        }
    }
}
