/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_openfeign_querydsl.querydsl_apt;

import com.querydsl.apt.hibernate.HibernateConfiguration;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static org.assertj.core.api.Assertions.assertThat;

public class HibernateConfigurationTest {
    @Test
    void configurationLoadsHibernateAnnotations() throws ClassNotFoundException {
        HibernateConfiguration configuration = new HibernateConfiguration(
                EMPTY_ROUND_ENVIRONMENT,
                EMPTY_PROCESSING_ENVIRONMENT,
                Entity.class,
                MappedSuperclass.class,
                Embeddable.class,
                Embedded.class,
                Transient.class);

        assertThat(configuration.getEntityAnnotations())
                .contains(Entity.class, MappedSuperclass.class, Embeddable.class);
    }

    private static final RoundEnvironment EMPTY_ROUND_ENVIRONMENT = new RoundEnvironment() {
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
            return Set.of();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(TypeElement annotationType) {
            return Set.of();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(Class<? extends Annotation> annotationType) {
            return Set.of();
        }
    };

    private static final ProcessingEnvironment EMPTY_PROCESSING_ENVIRONMENT = new ProcessingEnvironment() {
        @Override
        public Map<String, String> getOptions() {
            return Map.of();
        }

        @Override
        public Messager getMessager() {
            return null;
        }

        @Override
        public Filer getFiler() {
            return null;
        }

        @Override
        public Elements getElementUtils() {
            return null;
        }

        @Override
        public Types getTypeUtils() {
            return null;
        }

        @Override
        public SourceVersion getSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public Locale getLocale() {
            return Locale.getDefault();
        }
    };
}
