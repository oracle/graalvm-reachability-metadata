/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye.jandex;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.jandex.ArrayType;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.JandexReflection;
import org.junit.jupiter.api.Test;

public class JandexReflectionTest {
    @Test
    void loadRawTypeLoadsClassTypeByName() {
        ClassType stringType = ClassType.create(DotName.createSimple(String.class));

        Class<?> loadedType = JandexReflection.loadRawType(stringType);

        assertThat(loadedType).isSameAs(String.class);
    }

    @Test
    void loadRawTypeCreatesRuntimeArrayClass() {
        ClassType componentType = ClassType.create(DotName.createSimple(String.class));
        ArrayType arrayType = ArrayType.create(componentType, 2);

        Class<?> loadedType = JandexReflection.loadRawType(arrayType);

        assertThat(loadedType).isSameAs(String[][].class);
    }
}
