/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.maven.surefire.api.filter.SpecificTestClassFilter;
import org.apache.maven.surefire.api.report.RunMode;
import org.apache.maven.surefire.api.report.SimpleReportEntry;
import org.apache.maven.surefire.api.testset.TestArtifactInfo;
import org.apache.maven.surefire.api.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {

    @Test
    public void loadsClassesByNameThroughTheSuppliedClassLoader() throws ReflectiveOperationException {
        final ClassLoader classLoader = ReflectionUtilsTest.class.getClassLoader();
        final NoArgValue source = new NoArgValue();

        assertThat(ReflectionUtils.tryLoadClass(classLoader, SimpleReportEntry.class.getName()))
                .isEqualTo(SimpleReportEntry.class);
        assertThat(ReflectionUtils.loadClass(classLoader, TestArtifactInfo.class.getName()))
                .isEqualTo(TestArtifactInfo.class);
        assertThat(ReflectionUtils.reloadClass(classLoader, source)).isEqualTo(NoArgValue.class);
    }

    @Test
    public void instantiatesClassesWithSupportedConstructorShapes() {
        final ClassLoader classLoader = ReflectionUtilsTest.class.getClassLoader();

        final NoArgValue noArgValue = ReflectionUtils.instantiate(
                classLoader, NoArgValue.class.getName(), NoArgValue.class);
        final Object oneArgFilter = ReflectionUtils.instantiateOneArg(
                classLoader,
                SpecificTestClassFilter.class.getName(),
                String[].class,
                new String[] {ReflectionUtilsTest.class.getName()});
        final Constructor<TestArtifactInfo> testArtifactInfoConstructor = ReflectionUtils.tryGetConstructor(
                TestArtifactInfo.class,
                String.class,
                String.class);
        final TestArtifactInfo twoArgArtifact = ReflectionUtils.newInstance(
                testArtifactInfoConstructor,
                "provider-version",
                "tests");
        final Object newInstanceArtifact = ReflectionUtils.instantiateObject(
                TestArtifactInfo.class.getName(),
                new Class<?>[] {String.class, String.class},
                new Object[] {"provider-version", "main"},
                classLoader);

        assertThat(noArgValue.value()).isEqualTo("created");
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
        final SimpleReportEntry entry = new SimpleReportEntry(
                RunMode.NORMAL_RUN,
                1L,
                "reflection-source",
                "reflection-source-text",
                "reflection-name",
                "reflection-name-text");

        final Method getName = ReflectionUtils.getMethod(entry, "getName");
        final Method getSourceName = ReflectionUtils.tryGetMethod(SimpleReportEntry.class, "getSourceName");

        final String name = ReflectionUtils.invokeMethodWithArray(entry, getName);
        final String sourceName = ReflectionUtils.invokeMethodWithArray2(entry, getSourceName);

        assertThat(getSourceName).isNotNull();
        assertThat(name).isEqualTo("reflection-name");
        assertThat(sourceName).isEqualTo("reflection-source");
        assertThat(ReflectionUtils.tryGetMethod(SimpleReportEntry.class, "missingMethod")).isNull();
    }

    public static final class NoArgValue {
        public String value() {
            return "created";
        }
    }
}
