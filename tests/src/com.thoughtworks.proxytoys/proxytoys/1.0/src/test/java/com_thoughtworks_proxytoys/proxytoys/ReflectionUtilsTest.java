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
    public void roundTripsMethodsThroughProxytoysSerializationHelpers() throws Exception {
        Method selectedMethod = ReflectionUtils.getMatchingMethod(
                ReflectionUtils.class,
                "makeTypesArray",
                new Object[]{String.class, new Class<?>[]{Integer.class}});
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            ReflectionUtils.writeMethod(output, selectedMethod);
        }

        Method restoredMethod;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restoredMethod = ReflectionUtils.readMethod(input);
        }

        assertThat(restoredMethod).isEqualTo(selectedMethod);
        assertThat(restoredMethod.getDeclaringClass()).isEqualTo(ReflectionUtils.class);
        assertThat(restoredMethod.getName()).isEqualTo("makeTypesArray");
        assertThat(restoredMethod.getParameterTypes()).containsExactly(Class.class, Class[].class);
    }
}
