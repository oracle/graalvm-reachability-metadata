/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_ext;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.ProtectionDomain;

import org.junit.jupiter.api.Test;
import org.slf4j.instrumentation.LogTransformer;

public class LogTransformerTest {
    @Test
    void transformUsesProtectionDomainClassLoaderToCheckSlf4jAvailability() {
        ClassLoader classLoader = LogTransformerTest.class.getClassLoader();
        ProtectionDomain domain = new ProtectionDomain(null, null, classLoader, null);
        byte[] originalBytes = new byte[] {0, 1, 2, 3};

        assertThat(classLoader).isNotNull();

        LogTransformer transformer = new LogTransformer.Builder().build();
        byte[] transformedBytes = transformer.transform(
                classLoader,
                "example/InstrumentedTarget",
                null,
                domain,
                originalBytes);

        assertThat(transformedBytes).isSameAs(originalBytes);
    }
}
