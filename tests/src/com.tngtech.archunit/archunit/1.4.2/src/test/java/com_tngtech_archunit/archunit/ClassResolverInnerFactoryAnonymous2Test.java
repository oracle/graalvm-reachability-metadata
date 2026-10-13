/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.resolvers.ClassResolver;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class ClassResolverInnerFactoryAnonymous2Test {
    private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    @Test
    void importerInstantiatesConfiguredResolverThroughDefaultConstructor() {
        ArchConfiguration.withThreadLocalScope(configuration -> {
            configuration.setClassResolver(DefaultConstructorResolver.class);

            JavaClass imported = new ClassFileImporter().importClass(AnnotatedFixture.class);

            assertThat(imported.getName()).isEqualTo(AnnotatedFixture.class.getName());
            assertThat(CONSTRUCTIONS.get()).isPositive();
        });
    }

    public static final class DefaultConstructorResolver implements ClassResolver {
        public DefaultConstructorResolver() {
            CONSTRUCTIONS.incrementAndGet();
        }

        @Override
        public void setClassUriImporter(ClassUriImporter classUriImporter) {}

        @Override
        public Optional<JavaClass> tryResolve(String typeName) {
            return Optional.empty();
        }
    }
}
