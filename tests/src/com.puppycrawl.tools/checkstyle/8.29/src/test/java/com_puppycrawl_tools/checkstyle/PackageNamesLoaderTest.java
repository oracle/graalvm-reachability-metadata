/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_puppycrawl_tools.checkstyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.puppycrawl.tools.checkstyle.PackageNamesLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PackageNamesLoaderTest {
    @Test
    void loadsPackageNamesFromClasspathResources(@TempDir Path packageDirectory) throws Exception {
        Path packageFile = packageDirectory.resolve("checkstyle_packages.xml");
        Files.writeString(packageFile, """
            <?xml version="1.0"?>
            <!DOCTYPE checkstyle-packages PUBLIC
                "-//Checkstyle//DTD Package Names Configuration 1.0//EN"
                "https://checkstyle.org/dtds/packages_1_0.dtd">
            <checkstyle-packages>
              <package name="example">
                <package name="checks"/>
              </package>
            </checkstyle-packages>
            """);

        URL[] classpath = {packageDirectory.toUri().toURL()};
        try (URLClassLoader classLoader = new URLClassLoader(classpath, null)) {
            Set<String> packageNames = PackageNamesLoader.getPackageNames(classLoader);

            assertThat(packageNames).contains("example", "example.checks");
        }
    }
}
