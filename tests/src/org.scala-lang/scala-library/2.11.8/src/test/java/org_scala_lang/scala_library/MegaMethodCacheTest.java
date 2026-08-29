/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import scala.runtime.MegaMethodCache;

import static org.assertj.core.api.Assertions.assertThat;

public class MegaMethodCacheTest {

    @Test
    void findsPublicMethodsForReceiverClasses() {
        MegaMethodCache cache = new MegaMethodCache("substring", new Class<?>[] {int.class});

        Method method = cache.find(String.class);

        assertThat(method.getName()).isEqualTo("substring");
        assertThat(method.getDeclaringClass()).isEqualTo(String.class);
        assertThat(method.getParameterTypes()).containsExactly(int.class);
    }
}
