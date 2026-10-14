/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.resolvers.ClassResolverFromClasspath;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class ClassResolverFromClasspathTest {
    @Test
    void resolvesAClassResourceBeforeDelegatingItsImport() {
        AtomicReference<URI> resolvedUri = new AtomicReference<>();
        ClassResolverFromClasspath resolver = new ClassResolverFromClasspath();
        resolver.setClassUriImporter(uri -> {
            resolvedUri.set(uri);
            return Optional.<JavaClass>empty();
        });

        Optional<JavaClass> result = resolver.tryResolve(AnnotatedFixture.class.getName());

        assertThat(result).isEmpty();
        assertThat(resolvedUri.get()).isNotNull();
        assertThat(resolvedUri.get().toString()).endsWith("AnnotatedFixture.class");
    }
}
