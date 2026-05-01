/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_proxytoys.proxytoys;

import static org.assertj.core.api.Assertions.assertThat;

import com.thoughtworks.proxy.kit.ReflectionUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {
    @Test
    void findsMatchingPublicMethodFromRuntimeArguments() throws Exception {
        Method method = ReflectionUtils.getMatchingMethod(
                SampleMethods.class, "describe", new Object[] {"label", 7});

        assertThat(method.getDeclaringClass()).isEqualTo(SampleMethods.class);
        assertThat(method.getName()).isEqualTo("describe");
        assertThat(method.getParameterTypes()).containsExactly(String.class, int.class);
    }

    @Test
    void serializesAndDeserializesPublicMethodDescriptor() throws Exception {
        Method original = ReflectionUtils.getMatchingMethod(
                SampleMethods.class, "describe", new Object[] {"value", 42});

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            ReflectionUtils.writeMethod(output, original);
        }

        Method restored;
        ByteArrayInputStream serializedMethod = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream input = new ObjectInputStream(serializedMethod)) {
            restored = ReflectionUtils.readMethod(input);
        }

        assertThat(restored).isEqualTo(original);
        assertThat(restored.getDeclaringClass()).isEqualTo(SampleMethods.class);
        assertThat(restored.getParameterTypes()).containsExactly(String.class, int.class);
    }

    public static class SampleMethods {
        public String describe(String label, int count) {
            return label + ":" + count;
        }
    }
}
