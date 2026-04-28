/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_interpolation;

import org.codehaus.plexus.interpolation.reflection.MethodMap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodMapTest {
    @Test
    void matchesBoxedBooleanArgumentToPrimitiveBooleanParameter() throws Exception {
        MethodMap methodMap = new MethodMap();
        Method booleanMethod = MethodMapPrimitiveTarget.class.getMethod("accept", boolean.class);
        methodMap.add(booleanMethod);

        Method method = methodMap.find("accept", new Object[] {Boolean.TRUE });

        assertThat(method).isEqualTo(booleanMethod);
    }
}

final class MethodMapPrimitiveTarget {
    public void accept(boolean value) {
    }
}
