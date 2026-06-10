/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jandex;

import org.jboss.jandex.ArrayType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.JandexReflection;
import org.jboss.jandex.Type;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JandexReflectionTest {
    @Test
    void loadsRawArrayClassFromJandexClassType() {
        Type componentType = Type.create(DotName.createSimple(SampleType.class.getName()), Type.Kind.CLASS);
        ArrayType arrayType = ArrayType.create(componentType, 2);

        Class<?> loadedType = JandexReflection.loadRawType(arrayType);

        assertThat(loadedType).isEqualTo(SampleType[][].class);
    }

    static final class SampleType {
    }
}
