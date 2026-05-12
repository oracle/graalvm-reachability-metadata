/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_codegen;

import com.querydsl.codegen.ClassPathUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathUtilsTest {

    @Test
    void safeClassForNameLoadsClassWithSuppliedClassLoader() {
        ClassLoader classLoader = ClassPathUtilsTest.class.getClassLoader();

        Class<?> loadedClass = ClassPathUtils.safeClassForName(classLoader, String.class.getName());

        assertThat(loadedClass).isEqualTo(String.class);
    }
}
