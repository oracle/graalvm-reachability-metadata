/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.ClassUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {
    @Test
    void resolvesClassNamesIncludingArraysAndPrimitives() throws Exception {
        assertThat(ClassUtils.convertClassNamesToClasses(List.of("java.lang.String", "java.lang.Integer")))
                .containsExactly(String.class, Integer.class);
        assertThat(ClassUtils.getClass("java.lang.String[]")).isEqualTo(String[].class);
        assertThat(ClassUtils.getClass("int")).isEqualTo(int.class);
    }

    @Test
    void resolvesMethodsThroughPublicInterfaces() throws Exception {
        assertThat(ClassUtils.getPublicMethod(HiddenTask.class, "run").getDeclaringClass())
                .isEqualTo(Runnable.class);
        assertThat(ClassUtils.getPublicMethod(String.class, "substring", int.class).getReturnType())
                .isEqualTo(String.class);
    }

    private static class HiddenTask implements Runnable {
        @Override
        public void run() {
        }
    }
}
