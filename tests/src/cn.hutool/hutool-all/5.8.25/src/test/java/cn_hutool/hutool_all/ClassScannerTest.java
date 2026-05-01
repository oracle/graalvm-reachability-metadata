/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.lang.ClassScanner;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassScannerTest {
    private static final String PACKAGE_NAME = "cn.hutool.core.lang";
    private static final String PACKAGE_RESOURCE = "cn/hutool/core/lang";

    @TempDir
    Path temporaryDirectory;

    @Test
    public void scansClassFileResourceAndLoadsMatchingClassName() throws IOException {
        Path packageDirectory = temporaryDirectory.resolve("cn").resolve("hutool").resolve("core").resolve("lang");
        Files.createDirectories(packageDirectory);
        Files.createFile(packageDirectory.resolve("ClassScanner.class"));

        ClassScanner scanner = new ClassScanner(PACKAGE_NAME, clazz -> clazz == ClassScanner.class);
        scanner.setClassLoader(new PackageResourceClassLoader(packageDirectory.toUri().toURL()));

        try {
            Set<Class<?>> classes = scanner.scan();

            assertThat(classes).containsExactly(ClassScanner.class);
            assertThat(scanner.getClassesOfLoadError()).isEmpty();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class PackageResourceClassLoader extends ClassLoader {
        private final URL packageUrl;

        private PackageResourceClassLoader(URL packageUrl) {
            super(ClassScanner.class.getClassLoader());
            this.packageUrl = packageUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (PACKAGE_RESOURCE.equals(name)) {
                return Collections.enumeration(List.of(packageUrl));
            }
            return super.getResources(name);
        }
    }
}
