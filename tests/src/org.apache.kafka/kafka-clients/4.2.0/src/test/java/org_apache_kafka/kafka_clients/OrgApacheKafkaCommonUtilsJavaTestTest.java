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
    void testLoadKerberosLoginModuleUsesOpenJdkProviderOnSemeru() throws Exception {
        JavaTest test = new JavaTest();
        test.before();
        try {
            System.setProperty("java.vendor", "IBM Corporation");
            System.setProperty("java.runtime.name", "IBM Semeru Runtime Certified Edition");

            test.testLoadKerberosLoginModule();
        } finally {
            test.after();
        }
    }
}
