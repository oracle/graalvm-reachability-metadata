/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.importer.resolvers.ClassResolver;
import com.tngtech.archunit.core.importer.resolvers.ClassResolverFromClasspath;
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
}
