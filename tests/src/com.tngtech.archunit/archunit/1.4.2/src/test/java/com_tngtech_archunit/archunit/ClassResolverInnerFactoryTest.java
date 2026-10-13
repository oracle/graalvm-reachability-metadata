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
import com.tngtech.archunit.core.importer.resolvers.ClassResolver;
import com.tngtech.archunit.core.importer.resolvers.ClassResolverFromClasspath;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class ClassResolverInnerFactoryTest {
    @Test
    void createsClasspathResolverWhenClasspathResolutionIsEnabled() {
        ArchConfiguration.withThreadLocalScope(configuration -> {
            configuration.unsetClassResolver();
            configuration.setResolveMissingDependenciesFromClassPath(true);

            ClassResolver resolver = new ClassResolver.Factory().create();

            assertThat(resolver).isInstanceOf(ClassResolverFromClasspath.class);
        });
    }

    @Test
    void createsExplicitResolverThroughItsDefaultConstructor() {
        ArchConfiguration.withThreadLocalScope(configuration -> {
            configuration.setClassResolver(DefaultConstructorResolver.class);

            ClassResolver resolver = new ClassResolver.Factory().create();

            assertThat(resolver).isInstanceOf(DefaultConstructorResolver.class);
        });
    }

    public static final class DefaultConstructorResolver implements ClassResolver {
        public DefaultConstructorResolver() {}

        @Override
        public void setClassUriImporter(ClassUriImporter classUriImporter) {}

        @Override
        public Optional<JavaClass> tryResolve(String typeName) {
            return Optional.empty();
        }
    }
}
