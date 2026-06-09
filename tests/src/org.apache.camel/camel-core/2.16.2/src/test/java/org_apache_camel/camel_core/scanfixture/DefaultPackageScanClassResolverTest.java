/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core.scanfixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;

import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.apache.camel.spi.PackageScanFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultPackageScanClassResolverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void scansPackageResourcesAndLoadsMatchingClasses() throws Exception {
        String packageName = Candidate.class.getPackage().getName();
        Path packageDirectory = createPackageDirectory(packageName);
        Path markerClassFile = packageDirectory.resolve(simpleBinaryName(Candidate.class) + ".class");
        Files.createFile(markerClassFile);

        DefaultPackageScanClassResolver resolver = new DefaultPackageScanClassResolver();
        resolver.addClassLoader(new PackageDirectoryClassLoader(packageName, packageDirectory.toUri().toURL()));

        Set<Class<?>> foundClasses = resolver.findByFilter(new CandidateOnlyFilter(), packageName);

        assertThat(foundClasses).contains(Candidate.class);
    }

    private Path createPackageDirectory(String packageName) throws IOException {
        Path packageDirectory = temporaryDirectory.resolve(packageName.replace('.', '/'));
        Files.createDirectories(packageDirectory);
        return packageDirectory;
    }

    private String simpleBinaryName(Class<?> type) {
        return type.getName().substring(type.getPackage().getName().length() + 1);
    }

    public static class Candidate {
    }

    private static final class CandidateOnlyFilter implements PackageScanFilter {
        @Override
        public boolean matches(Class<?> type) {
            return Candidate.class.equals(type);
        }
    }

    private static final class PackageDirectoryClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL packageDirectoryUrl;

        private PackageDirectoryClassLoader(String packageName, URL packageDirectoryUrl) {
            super(DefaultPackageScanClassResolverTest.class.getClassLoader());
            this.resourceName = packageName.replace('.', '/') + "/";
            this.packageDirectoryUrl = packageDirectoryUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (resourceName.equals(name)) {
                return Collections.enumeration(Collections.singleton(packageDirectoryUrl));
            }
            return super.getResources(name);
        }
    }
}
