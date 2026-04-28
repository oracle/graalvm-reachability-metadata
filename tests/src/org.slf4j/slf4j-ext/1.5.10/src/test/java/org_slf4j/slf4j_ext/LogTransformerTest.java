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
    void transformChecksSlf4jAvailabilityWithTargetClassLoader() {
        LogTransformer transformer = new LogTransformer.Builder()
                .ignore(new String[] {"example/ignored/"})
                .build();
        ClassLoader applicationClassLoader = LogTransformerTest.class.getClassLoader();
        ProtectionDomain applicationDomain = new ProtectionDomain(null, null, applicationClassLoader, null);
        byte[] invalidClassBytes = new byte[] {0, 1, 2, 3};

        byte[] transformedBytes = transformer.transform(
                applicationClassLoader,
                "example/instrumentation/SampleService",
                null,
                applicationDomain,
                invalidClassBytes);

        assertThat(transformedBytes).isSameAs(invalidClassBytes);
    }
}
