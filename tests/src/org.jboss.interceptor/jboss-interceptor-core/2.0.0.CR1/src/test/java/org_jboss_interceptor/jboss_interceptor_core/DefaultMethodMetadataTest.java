/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;

import org.jboss.interceptor.reader.DefaultMethodMetadata;
import org.jboss.interceptor.spi.metadata.MethodMetadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultMethodMetadataTest {

    @Test
    void deserializesMethodMetadataByResolvingDeclaredMethod() throws Exception {
        final Method originalMethod = SampleTarget.class.getDeclaredMethod("combine", String.class, Integer.class);
        final MethodMetadata originalMetadata = DefaultMethodMetadata.of(originalMethod);

        final MethodMetadata deserializedMetadata = serializeAndDeserialize(originalMetadata);

        assertThat(deserializedMetadata).isInstanceOf(DefaultMethodMetadata.class);
        assertThat(deserializedMetadata.getJavaMethod()).isEqualTo(originalMethod);
        assertThat(deserializedMetadata.getReturnType()).isEqualTo(String.class);
        assertThat(deserializedMetadata.getSupportedInterceptionTypes()).isEmpty();
    }

    private static MethodMetadata serializeAndDeserialize(MethodMetadata metadata) throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(metadata);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (MethodMetadata) input.readObject();
        }
    }

    private static final class SampleTarget {
        private String combine(String prefix, Integer value) {
            return prefix + value;
        }
    }
}
