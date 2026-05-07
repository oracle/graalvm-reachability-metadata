/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_support;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.support.scan.DefaultPackageScanClassResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultPackageScanClassResolverTest {
    @Test
    void addIfMatchingLoadsMatchingClassWithContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(DefaultPackageScanClassResolverTest.class.getClassLoader());
        try {
            ExposedPackageScanClassResolver resolver = new ExposedPackageScanClassResolver();
            Set<Class<?>> matchedClasses = new LinkedHashSet<>();
            PackageScanFilter targetOnlyFilter = type -> ScanTarget.class.equals(type);
            String targetClassFileName = ScanTarget.class.getName().replace('.', '/') + ".class";

            resolver.addClassIfMatching(targetOnlyFilter, targetClassFileName, matchedClasses);

            assertThat(matchedClasses).containsExactly(ScanTarget.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static final class ExposedPackageScanClassResolver extends DefaultPackageScanClassResolver {
        void addClassIfMatching(PackageScanFilter filter, String fullyQualifiedName, Set<Class<?>> classes) {
            addIfMatching(filter, fullyQualifiedName, classes);
        }
    }

    private static final class ScanTarget {
    }
}
