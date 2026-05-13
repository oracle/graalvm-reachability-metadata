/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.maven_shared_utils;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

import org.apache.maven.shared.utils.cli.shell.BourneShell;
import org.apache.maven.shared.utils.introspection.ReflectionValueExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionValueExtractorTest {
    @Test
    void evaluatesIndexedPropertyFromGetterResult() throws Exception {
        BourneShell shell = new BourneShell();

        Object value = ReflectionValueExtractor.evaluate("shellArgsList[0]", shell, false);

        assertThat(value).isEqualTo("-c");
    }

    @Test
    void evaluatesMappedPropertyFromGetterResult() throws Exception {
        String propertyName = "mavensharedutilstest";
        String previousValue = System.getProperty(propertyName);
        System.setProperty(propertyName, "mapped-value");

        try {
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            Object value = ReflectionValueExtractor.evaluate("systemProperties(" + propertyName + ")", runtime, false);

            assertThat(value).isEqualTo("mapped-value");
        } finally {
            if (previousValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previousValue);
            }
        }
    }
}
