/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

/** Verifies classfile annotation arrays are decoded with their declared element types. */
public class ClassFileAnnotationDelegateTest {
    @Test
    void readsEnumAndPrimitiveArraysFromClassfile() throws Exception {
        MergedAnnotation<Access> annotation = new SimpleMetadataReaderFactory(getClass().getClassLoader())
                .getMetadataReader(Endpoint.class.getName()).getAnnotationMetadata().getAnnotations()
                .get(Access.class);

        assertThat(annotation.getEnumArray("roles", Role.class)).containsExactly(Role.READER, Role.WRITER);
        assertThat(annotation.getIntArray("ports")).containsExactly(8080, 8443);
        assertThat(annotation.synthesize().roles()).containsExactly(Role.READER, Role.WRITER);
    }

    public enum Role {
        READER, WRITER
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Access {
        Role[] roles();
        int[] ports();
    }

    @Access(roles = {Role.READER, Role.WRITER}, ports = {8080, 8443})
    public static class Endpoint { }
}
