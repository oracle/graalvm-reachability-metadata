/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_platform.junit_platform_commons;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.ReflectionSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class ClasspathScannerTest {

    @Test
    void scansPackageResourcesUsingDefaultClassLoader() {
        String packageName = ClasspathScannerTest.class.getPackageName();
        String scannerTestName = ClasspathScannerTest.class.getName();

        List<Class<?>> classes = ReflectionSupport.findAllClassesInPackage(packageName,
                candidate -> candidate == ClasspathScannerTest.class, name -> name.equals(scannerTestName));

        assertThat(classes).doesNotHaveDuplicates();
        assertThat(classes).allSatisfy(candidate -> assertThat(candidate).isSameAs(ClasspathScannerTest.class));
    }
}
