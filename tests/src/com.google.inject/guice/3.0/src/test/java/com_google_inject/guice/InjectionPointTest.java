/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_inject.guice;

import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.InjectionPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InjectionPointTest {
    @Test
    void discoversAnnotatedAndDefaultConstructors() {
        InjectionPoint annotated = InjectionPoint.forConstructorOf(ConstructorClient.class);
        assertEquals(ConstructorClient.class, annotated.getMember().getDeclaringClass());
        assertEquals(Key.get(String.class, Names.named("endpoint")), annotated.getDependencies().get(0).getKey());
        assertFalse(annotated.isOptional());

        InjectionPoint defaultConstructor = InjectionPoint.forConstructorOf(DefaultClient.class);
        assertEquals(DefaultClient.class, defaultConstructor.getMember().getDeclaringClass());
        assertTrue(defaultConstructor.getDependencies().isEmpty());
    }

    @Test
    void resolvesInheritedGenericFieldsAndMethods() {
        Set<InjectionPoint> points = InjectionPoint.forInstanceMethodsAndFields(new TypeLiteral<GenericClient<String>>() { });
        assertEquals(List.of("value", "initialize"), points.stream().map(point -> point.getMember().getName()).toList());
        for (InjectionPoint point : points) {
            assertEquals(Key.get(String.class), point.getDependencies().get(0).getKey());
        }
        Set<InjectionPoint> statics = InjectionPoint.forStaticMethodsAndFields(StaticClient.class);
        assertEquals(Set.of("value", "initialize"), statics.stream().map(point -> point.getMember().getName()).collect(toSet()));
    }

    @Test
    void rejectsBindingAnnotationsOnInjectionMethods() {
        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> InjectionPoint.forInstanceMethodsAndFields(MisplacedQualifier.class));
        assertTrue(error.getMessage().contains("misplaced"));
    }

    static final class ConstructorClient {
        @Inject
        private ConstructorClient(@Named("endpoint") String endpoint) {
        }
    }

    static final class DefaultClient {
    }

    static class GenericBase<T> {
        @Inject T value;
    }

    static final class GenericClient<T> extends GenericBase<T> {
        @Inject
        private void initialize(T dependency) {
        }
    }

    static final class StaticClient {
        @Inject static String value;

        @Inject
        private static void initialize(String dependency) {
        }
    }

    static final class MisplacedQualifier {
        @Inject
        @Named("misplaced")
        private void initialize(String dependency) {
        }
    }
}
