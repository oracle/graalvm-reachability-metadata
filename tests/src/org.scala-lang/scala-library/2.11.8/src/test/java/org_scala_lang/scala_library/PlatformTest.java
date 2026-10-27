/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library;

import org.junit.jupiter.api.Test;

import scala.compat.Platform$;

import static org.assertj.core.api.Assertions.assertThat;

public class PlatformTest {

    @Test
    void loadsClassesAndCreatesTypedArrays() throws Exception {
        Platform$ platform = Platform$.MODULE$;

        Class<?> loadedClass = platform.getClassForName(String.class.getName());
        Object array = platform.createArray(loadedClass, 3);

        assertThat(loadedClass).isEqualTo(String.class);
        assertThat(array).isInstanceOf(String[].class);
        assertThat((String[]) array).hasSize(3).containsOnlyNulls();
    }
}
