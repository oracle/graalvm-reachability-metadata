/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_apt;

import com.querydsl.apt.DefaultConfiguration;
import com.querydsl.codegen.DefaultVariableNameFunction;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.querydsl.apt.APTOptions.QUERYDSL_VARIABLE_NAME_FUNCTION_CLASS;
import static org.assertj.core.api.Assertions.assertThat;

public class DefaultConfigurationTest {

    @Test
    @SuppressWarnings("deprecation")
    void loadsConfiguredVariableNameFunctionClass() {
        Map<String, String> options = Map.of(
                QUERYDSL_VARIABLE_NAME_FUNCTION_CLASS,
                DefaultVariableNameFunction.class.getName()
        );

        DefaultConfiguration configuration = new DefaultConfiguration(
                new EmptyRoundEnvironment(),
                options,
                Collections.emptySet(),
                null,
                Entity.class,
                null,
                null,
                null,
                null
        );

        assertThat(configuration.getVariableNameFunction())
                .isInstanceOf(DefaultVariableNameFunction.class)
                .isNotSameAs(DefaultVariableNameFunction.INSTANCE);
    }

    private @interface Entity {
    }

    private static final class EmptyRoundEnvironment implements RoundEnvironment {

        @Override
        public boolean processingOver() {
            return false;
        }

        @Override
        public boolean errorRaised() {
            return false;
        }

        @Override
        public Set<? extends Element> getRootElements() {
            return Collections.emptySet();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(TypeElement annotation) {
            return Collections.emptySet();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(Class<? extends Annotation> annotation) {
            return Collections.emptySet();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWithAny(TypeElement... annotations) {
            return Collections.emptySet();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWithAny(Set<Class<? extends Annotation>> annotations) {
            return Collections.emptySet();
        }
    }
}
