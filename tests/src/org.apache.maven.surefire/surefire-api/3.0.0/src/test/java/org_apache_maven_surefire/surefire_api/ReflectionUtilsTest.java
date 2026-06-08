/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.maven.surefire.SpecificTestClassFilter;
import org.apache.maven.surefire.report.SimpleReportEntry;
import org.apache.maven.surefire.testset.TestArtifactInfo;
import org.apache.maven.surefire.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {

    @Test
    public void loadsClassesByNameThroughTheSuppliedClassLoader() {
        final ClassLoader classLoader = ReflectionUtilsTest.class.getClassLoader();

        assertThat(ReflectionUtils.tryLoadClass(classLoader, SimpleReportEntry.class.getName()))
                .isEqualTo(SimpleReportEntry.class);
        assertThat(ReflectionUtils.loadClass(classLoader, TestArtifactInfo.class.getName()))
                .isEqualTo(TestArtifactInfo.class);
    }

    @Test
    public void instantiatesClassesWithSupportedConstructorShapes() {
        final ClassLoader classLoader = ReflectionUtilsTest.class.getClassLoader();

        final SimpleReportEntry noArgEntry = ReflectionUtils.instantiate(
                classLoader, SimpleReportEntry.class.getName(), SimpleReportEntry.class);
        final Object oneArgFilter = ReflectionUtils.instantiateOneArg(
                classLoader,
                SpecificTestClassFilter.class.getName(),
                String[].class,
                new String[] {ReflectionUtilsTest.class.getName()});
        final Object twoArgArtifact = ReflectionUtils.instantiateTwoArgs(
                classLoader,
                TestArtifactInfo.class.getName(),
                String.class,
                "provider-version",
                String.class,
                "tests");
        final Object newInstanceArtifact = ReflectionUtils.instantiateObject(
                TestArtifactInfo.class.getName(),
                new Class[] {String.class, String.class},
                new Object[] {"provider-version", "main"},
                classLoader);

        assertThat(noArgEntry.getName()).isEqualTo("null");
        assertThat(oneArgFilter).isInstanceOf(SpecificTestClassFilter.class);
        assertThat(twoArgArtifact)
                .isInstanceOfSatisfying(TestArtifactInfo.class, artifact -> {
                    assertThat(artifact.getVersion()).isEqualTo("provider-version");
                    assertThat(artifact.getClassifier()).isEqualTo("tests");
                });
        assertThat(newInstanceArtifact)
                .isInstanceOfSatisfying(TestArtifactInfo.class, artifact -> {
                    assertThat(artifact.getVersion()).isEqualTo("provider-version");
                    assertThat(artifact.getClassifier()).isEqualTo("main");
                });
    }

    @Test
    public void findsAndInvokesPublicMethods() throws InvocationTargetException {
        final SimpleReportEntry entry = new SimpleReportEntry("reflection-source", "reflection-name");

        final Method getName = ReflectionUtils.getMethod(entry, "getName");
        final Method getSourceName = ReflectionUtils.tryGetMethod(SimpleReportEntry.class, "getSourceName");

        assertThat(getSourceName).isNotNull();
        assertThat(ReflectionUtils.invokeMethodWithArray(entry, getName)).isEqualTo("reflection-name");
        assertThat(ReflectionUtils.invokeMethodWithArray2(entry, getSourceName)).isEqualTo("reflection-source");
        assertThat(ReflectionUtils.tryGetMethod(SimpleReportEntry.class, "missingMethod")).isNull();
    }
}
