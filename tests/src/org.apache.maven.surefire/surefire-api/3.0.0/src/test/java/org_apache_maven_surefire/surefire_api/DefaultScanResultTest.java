/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.apache.maven.surefire.api.util.DefaultScanResult;
import org.apache.maven.surefire.api.util.TestsToRun;
import org.junit.jupiter.api.Test;

public class DefaultScanResultTest {

    @Test
    public void appliesFilterAfterLoadingEachScannedClassByName() {
        final ClassLoader classLoader = DefaultScanResultTest.class.getClassLoader();
        final DefaultScanResult scanResult = new DefaultScanResult(Arrays.asList(
                DefaultScanResult.class.getName(),
                TestsToRun.class.getName()));

        final TestsToRun acceptedClasses = scanResult.applyFilter(
                testClass -> testClass == DefaultScanResult.class,
                classLoader);
        final List<Class<?>> rejectedClasses = scanResult.getClassesSkippedByValidation(
                testClass -> testClass == DefaultScanResult.class,
                classLoader);

        assertThat(acceptedClasses.getLocatedClasses()).containsExactly(DefaultScanResult.class);
        assertThat(rejectedClasses).containsExactly(TestsToRun.class);
    }
}
