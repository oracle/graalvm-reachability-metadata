/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javassist.runtime.Desc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class DescTest {
    @AfterEach
    void resetDescState() {
        Desc.useContextClassLoader = false;
    }

    @Test
    void resolvesClassesAndDescriptorsWithDefaultClassLoading() {
        Desc.useContextClassLoader = false;

        assertThat(Desc.getClazz(String.class.getName())).isSameAs(String.class);
        assertThat(Desc.getType("Ljava/lang/String;")).isSameAs(String.class);
        assertThat(Desc.getParams("(Ljava/lang/String;I[Ljava/lang/Object;)"))
                .containsExactly(String.class, int.class, Object[].class);
    }

    @Test
    void resolvesClassesAndDescriptorsWithContextClassLoader() {
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        Desc.useContextClassLoader = true;
        Thread.currentThread().setContextClassLoader(DescTest.class.getClassLoader());
        try {
            assertThat(Desc.getClazz(List.class.getName())).isSameAs(List.class);
            assertThat(Desc.getType("[Ljava/lang/String;")).isSameAs(String[].class);
            assertThat(Desc.getParams("([Ljava/lang/String;Ljava/util/List;)"))
                    .containsExactly(String[].class, List.class);
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }
}
