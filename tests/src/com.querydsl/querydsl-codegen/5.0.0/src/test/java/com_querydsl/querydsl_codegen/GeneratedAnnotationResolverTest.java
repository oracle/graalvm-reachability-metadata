/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_codegen;

import java.lang.annotation.Annotation;

import javax.annotation.processing.Generated;

import com.querydsl.codegen.GeneratedAnnotationResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratedAnnotationResolverTest {

    @Test
    void resolveLoadsConfiguredGeneratedAnnotationClass() {
        Class<? extends Annotation> resolvedClass = GeneratedAnnotationResolver.resolve(Generated.class.getName());

        assertThat(resolvedClass).isEqualTo(Generated.class);
    }
}
