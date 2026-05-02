/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import org.apache.maven.surefire.util.DefaultScanResult;
import org.apache.maven.surefire.util.ScannerFilter;
import org.apache.maven.surefire.util.TestsToRun;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultScanResultTest {
    @Test
    void appliesFilterByLoadingScannedClassNames() {
        Class<?> expectedClass = DefaultScanResultTest.class;
        DefaultScanResult scanResult = new DefaultScanResult(Collections.singletonList(expectedClass.getName()));
        ScannerFilter scannerFilter = testClass -> testClass == expectedClass;

        try {
            TestsToRun testsToRun = scanResult.applyFilter(scannerFilter, expectedClass.getClassLoader());

            assertThat(testsToRun.getLocatedClasses()).containsExactly(expectedClass);
            assertThat(testsToRun.getClassByName(expectedClass.getName())).isEqualTo(expectedClass);
        } catch (Error e) {
            if (!NativeImageSupport.isUnsupportedFeatureError(e)) {
                throw e;
            }
        }
    }

    @Test
    void reportsClassesSkippedByValidationAfterLoadingClassNames() {
        Class<?> skippedClass = DefaultScanResultTest.class;
        DefaultScanResult scanResult = new DefaultScanResult(Collections.singletonList(skippedClass.getName()));
        ScannerFilter rejectingFilter = testClass -> false;

        try {
            List<Class<?>> skippedClasses = scanResult.getClassesSkippedByValidation(
                    rejectingFilter,
                    skippedClass.getClassLoader()
            );

            assertThat(skippedClasses).containsExactly(skippedClass);
        } catch (Error e) {
            if (!NativeImageSupport.isUnsupportedFeatureError(e)) {
                throw e;
            }
        }
    }
}
