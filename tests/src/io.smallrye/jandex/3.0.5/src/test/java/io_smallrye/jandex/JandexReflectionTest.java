/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye.jandex;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.jboss.jandex.ArrayType;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.JandexReflection;
import org.junit.jupiter.api.Test;

public class JandexReflectionTest {
    @Test
    void loadRawTypeCreatesRuntimeArrayClass() {
        ClassType stringType = ClassType.create(DotName.createSimple(String.class));
        ArrayType stringMatrixType = ArrayType.create(stringType, 2);

        assertSame(String[][].class, JandexReflection.loadRawType(stringMatrixType));
    }
}
