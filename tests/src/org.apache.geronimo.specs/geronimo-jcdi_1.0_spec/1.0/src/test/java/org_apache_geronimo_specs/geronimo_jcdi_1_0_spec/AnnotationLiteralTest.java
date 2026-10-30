/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_jcdi_1_0_spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.New;
import javax.enterprise.inject.Typed;
import javax.enterprise.util.AnnotationLiteral;
import org.junit.jupiter.api.Test;

public class AnnotationLiteralTest {
    @Test
    void comparesQualifiersByAnnotationTypeAndMemberValue() {
        New first = new NewLiteral(String.class);
        New same = new NewLiteral(String.class);
        New different = new NewLiteral(Integer.class);

        assertEquals(New.class, first.annotationType());
        assertEquals(String.class, first.value());
        assertEquals(first, same);
        assertEquals(same, first);
        assertEquals(first, first);
        assertNotEquals(first, different);
        assertFalse(first.equals(null));
        assertFalse(first.equals("qualifier"));
        assertNotEquals(first, new DefaultLiteral());
    }

    @Test
    void usesAnnotationHashContractForQualifierLookup() {
        New qualifier = new NewLiteral(String.class);
        int expectedHash = (127 * "value".hashCode()) ^ String.class.hashCode();
        Set<Annotation> qualifiers = new HashSet<>();

        assertEquals(expectedHash, qualifier.hashCode());
        assertTrue(qualifiers.add(qualifier));
        assertFalse(qualifiers.add(new NewLiteral(String.class)));
        assertTrue(qualifiers.add(new NewLiteral(Integer.class)));
        assertEquals(2, qualifiers.size());
        assertTrue(qualifiers.contains(new NewLiteral(String.class)));
    }

    @Test
    void describesQualifierTypeAndMember() {
        New qualifier = new NewLiteral(String.class);

        assertEquals("@" + New.class.getName() + "(value=" + String.class + ")", qualifier.toString());
    }

    @Test
    void supportsMarkerQualifiersAndInheritedLiterals() {
        Default first = new DefaultLiteral();
        Default inherited = new InheritedDefaultLiteral();

        assertEquals(Default.class, inherited.annotationType());
        assertEquals(first, inherited);
        assertEquals(inherited, first);
        assertEquals(0, inherited.hashCode());
        assertEquals("@" + Default.class.getName() + "()", inherited.toString());
        assertNotEquals(first, new AnyLiteral());
    }

    @Test
    void comparesAndHashesArrayMembersByContents() {
        Typed first = new TypedLiteral(String.class, CharSequence.class);
        Typed same = new TypedLiteral(String.class, CharSequence.class);
        Typed different = new TypedLiteral(Integer.class, Number.class);
        int expectedHash = (127 * "value".hashCode())
                ^ Arrays.hashCode(new Class<?>[] {String.class, CharSequence.class});

        assertEquals(Typed.class, first.annotationType());
        assertEquals(first, same);
        assertEquals(same, first);
        assertEquals(expectedHash, first.hashCode());
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, different);
        assertNotEquals(first, new TypedLiteral(CharSequence.class, String.class));
        assertNotEquals(first, new TypedLiteral(String.class));
    }

    @Test
    void supportsEmptyBeanTypeArrays() {
        Typed first = new TypedLiteral();
        Typed same = new TypedLiteral();

        assertEquals(0, first.value().length);
        assertEquals(first, same);
        assertEquals((127 * "value".hashCode()) ^ Arrays.hashCode(new Class<?>[0]), first.hashCode());
        assertNotEquals(first, new TypedLiteral(Object.class));
    }

    private static final class NewLiteral extends AnnotationLiteral<New> implements New {
        private final Class<?> beanType;

        private NewLiteral(Class<?> beanType) {
            this.beanType = beanType;
        }

        @Override
        public Class<?> value() {
            return beanType;
        }
    }

    private static class DefaultLiteral extends AnnotationLiteral<Default> implements Default {
    }

    private static final class InheritedDefaultLiteral extends DefaultLiteral {
    }

    private static final class AnyLiteral extends AnnotationLiteral<Any> implements Any {
    }

    private static final class TypedLiteral extends AnnotationLiteral<Typed> implements Typed {
        private final Class<?>[] beanTypes;

        private TypedLiteral(Class<?>... beanTypes) {
            this.beanTypes = beanTypes.clone();
        }

        @Override
        public Class<?>[] value() {
            return beanTypes.clone();
        }
    }
}
