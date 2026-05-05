/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.internal.LettuceClassUtils;
import org.junit.jupiter.api.Test;

public class LettuceClassUtilsTest {

    @Test
    void loadsClassesByNameUsingDefaultClassLoader() throws ClassNotFoundException {
        String className = String.class.getName();

        assertThat(LettuceClassUtils.forName(className)).isSameAs(String.class);
        assertThat(LettuceClassUtils.findClass(className)).isSameAs(String.class);
        assertThat(LettuceClassUtils.isPresent(className)).isTrue();
    }

    @Test
    void loadsNestedClassUsingCanonicalNameFallback() throws ClassNotFoundException {
        String canonicalName = NestedLoadTarget.class.getCanonicalName();

        assertThat(LettuceClassUtils.forName(canonicalName)).isSameAs(NestedLoadTarget.class);
    }

    public static class NestedLoadTarget {
    }
}
