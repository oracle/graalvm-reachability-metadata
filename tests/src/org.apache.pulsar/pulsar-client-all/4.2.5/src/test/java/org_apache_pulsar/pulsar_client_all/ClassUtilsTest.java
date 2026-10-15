/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.pulsar.shade.org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ClassUtilsTest {
    @Test
    void resolvesNamesArraysAndPublicInterfaceMethods() throws Exception {
        List<Class<?>> classes = ClassUtils.convertClassNamesToClasses(
                List.of(String.class.getName(), "missing.example.Type"));
        Class<?> entryArray = ClassUtils.getClass(
                getClass().getClassLoader(), "java.util.Map.Entry[]", true);
        Method size = ClassUtils.getPublicMethod(
                Collections.unmodifiableList(List.of("entry")).getClass(), "size");

        assertThat(classes).containsExactly(String.class, null);
        assertThat(entryArray).isEqualTo(Map.Entry[].class);
        assertThat(size.getName()).isEqualTo("size");
        assertThat(size.getDeclaringClass()).isPublic();
    }
}
