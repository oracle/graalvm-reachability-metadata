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
import org.apache.maven.surefire.shared.utils.io.Java7Support;
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

        final Java7Support java7Support = ReflectionUtils.instantiate(
                classLoader, Java7Support.class.getName(), Java7Support.class);
        final Object oneArgFilter = ReflectionUtils.instantiateOneArg(
                classLoader,
                SpecificTestClassFilter.class.getName(),
                String[].class,
                new String[] {ReflectionUtilsTest.class.getName()});
        final Object newInstanceArtifact = ReflectionUtils.instantiateObject(
                TestArtifactInfo.class.getName(),
                new Class<?>[] {String.class, String.class},
                new Object[] {"provider-version", "main"},
                classLoader);

        assertThat(java7Support).isInstanceOf(Java7Support.class);
        assertThat(oneArgFilter).isInstanceOf(SpecificTestClassFilter.class);
        assertThat(newInstanceArtifact)
                .isInstanceOfSatisfying(TestArtifactInfo.class, artifact -> {
                    assertThat(artifact.getVersion()).isEqualTo("provider-version");
                    assertThat(artifact.getClassifier()).isEqualTo("main");
                });
    }

    @Test
    public void returnsPublicConstructorsWhenPresent() {
        final Constructor<SpecificTestClassFilter> constructor = ReflectionUtils.tryGetConstructor(
                SpecificTestClassFilter.class, String[].class);

        assertThat(constructor).isNotNull();

        final SpecificTestClassFilter filter = ReflectionUtils.newInstance(
                constructor, (Object) new String[] {ReflectionUtilsTest.class.getName()});
        assertThat(filter).isInstanceOf(SpecificTestClassFilter.class);
        assertThat(ReflectionUtils.tryGetConstructor(SpecificTestClassFilter.class, Integer.class)).isNull();
    }

    @Test
    public void reloadsTheSourceClassThroughTheSuppliedClassLoader() throws ReflectiveOperationException {
        final ClassLoader classLoader = ReflectionUtilsTest.class.getClassLoader();
        final SimpleReportEntry entry = new SimpleReportEntry(
                RunMode.NORMAL_RUN,
                2L,
                "reload-source",
                "reload-source-text",
                "reload-name",
                "reload-name-text");

        final Class<?> reloadedClass = ReflectionUtils.reloadClass(classLoader, entry);

        assertThat(reloadedClass).isEqualTo(SimpleReportEntry.class);
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
}
