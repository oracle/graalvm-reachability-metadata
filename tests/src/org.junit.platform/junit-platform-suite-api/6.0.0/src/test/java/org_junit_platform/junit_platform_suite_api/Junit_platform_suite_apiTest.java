/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_platform.junit_platform_suite_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.platform.suite.api.AfterSuite;
import org.junit.platform.suite.api.BeforeSuite;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ConfigurationParameters;
import org.junit.platform.suite.api.ConfigurationParametersResource;
import org.junit.platform.suite.api.ConfigurationParametersResources;
import org.junit.platform.suite.api.DisableParentConfigurationParameters;
import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.ExcludeEngines;
import org.junit.platform.suite.api.ExcludePackages;
import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.IncludePackages;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.Select;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.SelectClasspathResources;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.SelectFile;
import org.junit.platform.suite.api.SelectFiles;
import org.junit.platform.suite.api.SelectMethod;
import org.junit.platform.suite.api.SelectMethods;
import org.junit.platform.suite.api.SelectModules;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.SelectUris;
import org.junit.platform.suite.api.Selects;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.junit.platform.suite.api.UseTechnicalNames;

public class Junit_platform_suite_apiTest {
    @Test
    void suiteLevelOptionsAndFiltersRetainTheirValues() {
        Suite suite = annotationOn(ComprehensiveSuite.class, Suite.class);
        SuiteDisplayName displayName = annotationOn(ComprehensiveSuite.class, SuiteDisplayName.class);

        assertThat(suite.failIfNoTests()).isFalse();
        assertThat(displayName.value()).isEqualTo("Comprehensive suite API fixture");
        assertThat(annotationOn(ComprehensiveSuite.class, UseTechnicalNames.class)).isNotNull();
        assertThat(annotationOn(ComprehensiveSuite.class, DisableParentConfigurationParameters.class)).isNotNull();
        assertThat(annotationOn(ComprehensiveSuite.class, IncludeEngines.class).value()).containsExactly(
                "junit-jupiter", "custom-engine");
        assertThat(annotationOn(ComprehensiveSuite.class, ExcludeEngines.class).value()).containsExactly("vintage");
        assertThat(annotationOn(ComprehensiveSuite.class, IncludeTags.class).value()).containsExactly("fast", "native");
        assertThat(annotationOn(ComprehensiveSuite.class, ExcludeTags.class).value()).containsExactly("slow");
        assertThat(annotationOn(ComprehensiveSuite.class, IncludePackages.class).value()).containsExactly(
                "org.example.included", "org.example.shared");
        assertThat(annotationOn(ComprehensiveSuite.class, ExcludePackages.class).value()).containsExactly(
                "org.example.excluded");
        assertThat(annotationOn(ComprehensiveSuite.class, IncludeClassNamePatterns.class).value()).containsExactly(
                ".*Tests?", ".*Spec");
        assertThat(annotationOn(ComprehensiveSuite.class, ExcludeClassNamePatterns.class).value()).containsExactly(
                ".*IT");
    }

    @Test
    void selectorsRetainTypedValuesAndLocationCoordinates() {
        SelectClasses classes = annotationOn(ComprehensiveSuite.class, SelectClasses.class);
        SelectClasspathResource[] classpathResources = annotationsByTypeOn(ComprehensiveSuite.class,
                SelectClasspathResource.class);
        SelectFile[] files = annotationsByTypeOn(ComprehensiveSuite.class, SelectFile.class);
        SelectMethod[] methods = annotationsByTypeOn(ComprehensiveSuite.class, SelectMethod.class);

        assertThat(classes.value()).containsExactly(SelectedFixture.class, AnotherSelectedFixture.class);
        assertThat(classes.names()).containsExactly("java.lang.String", "java.util.ArrayList");
        assertThat(annotationOn(ComprehensiveSuite.class, SelectPackages.class).value()).containsExactly(
                "org.example.tests", "org.example.moretests");
        assertThat(annotationOn(ComprehensiveSuite.class, SelectDirectories.class).value()).containsExactly(
                "src/test/java", "src/integrationTest/java");
        assertThat(annotationOn(ComprehensiveSuite.class, SelectModules.class).value()).containsExactly(
                "java.base", "java.logging");
        assertThat(annotationOn(ComprehensiveSuite.class, SelectUris.class).value()).containsExactly(
                "file:///tmp/suite-one", "classpath:/suite-two");
        Select[] selects = annotationsByTypeOn(ComprehensiveSuite.class, Select.class);
        assertThat(selects).hasSize(2);
        assertThat(selects[0].value()).containsExactly("class:org.example.First");
        assertThat(selects[1].value()).containsExactly("method:org.example.Second#test");
        assertThat(classpathResources).hasSize(2);
        assertThat(classpathResources[0].value()).isEqualTo("junit-platform.properties");
        assertThat(classpathResources[0].line()).isEqualTo(7);
        assertThat(classpathResources[0].column()).isEqualTo(3);
        assertThat(classpathResources[1].value()).isEqualTo("META-INF/services/org.junit.platform.engine.TestEngine");
        assertThat(classpathResources[1].line()).isEqualTo(1);
        assertThat(classpathResources[1].column()).isZero();
        assertThat(files).hasSize(2);
        assertThat(files[0].value()).isEqualTo("build.gradle");
        assertThat(files[0].line()).isEqualTo(12);
        assertThat(files[0].column()).isEqualTo(4);
        assertThat(files[1].value()).isEqualTo("settings.gradle");
        assertThat(files[1].line()).isZero();
        assertThat(files[1].column()).isZero();
        assertThat(methods).hasSize(2);
        assertThat(methods[0].value()).isEqualTo("org.example.CalculatorTests#adds(int, int)");
        assertThat(methods[0].type()).isEqualTo(SelectedFixture.class);
        assertThat(methods[0].typeName()).isEqualTo("org.example.CalculatorTests");
        assertThat(methods[0].name()).isEqualTo("adds");
        assertThat(methods[0].parameterTypes()).containsExactly(int.class, int.class);
        assertThat(methods[0].parameterTypeNames()).isEqualTo("int, int");
        assertThat(methods[1].name()).isEqualTo("emptyTest");
        assertThat(methods[1].parameterTypes()).isEmpty();
    }

    @Test
    void repeatableAnnotationsAreExposedThroughTheirContainerTypes() {
        ConfigurationParameters parameters = annotationOn(ComprehensiveSuite.class, ConfigurationParameters.class);
        ConfigurationParametersResources resources = annotationOn(ComprehensiveSuite.class,
                ConfigurationParametersResources.class);
        Selects selects = annotationOn(ComprehensiveSuite.class, Selects.class);
        SelectClasspathResources classpathResources = annotationOn(ComprehensiveSuite.class,
                SelectClasspathResources.class);
        SelectFiles files = annotationOn(ComprehensiveSuite.class, SelectFiles.class);
        SelectMethods methods = annotationOn(ComprehensiveSuite.class, SelectMethods.class);

        assertThat(parameters.value()).hasSize(2);
        assertThat(parameters.value()[0].key()).isEqualTo("junit.jupiter.conditions.deactivate");
        assertThat(parameters.value()[0].value()).isEqualTo("org.example.DisabledCondition");
        assertThat(parameters.value()[1].key()).isEqualTo("custom.suite.mode");
        assertThat(parameters.value()[1].value()).isEqualTo("native-friendly");
        assertThat(resources.value()).extracting(ConfigurationParametersResource::value).containsExactly(
                "junit-platform.properties", "suite-overrides.properties");
        assertThat(selects.value()).hasSize(2);
        assertThat(selects.value()[0].value()).containsExactly("class:org.example.First");
        assertThat(selects.value()[1].value()).containsExactly("method:org.example.Second#test");
        assertThat(classpathResources.value()).extracting(SelectClasspathResource::value).containsExactly(
                "junit-platform.properties", "META-INF/services/org.junit.platform.engine.TestEngine");
        assertThat(files.value()).extracting(SelectFile::value).containsExactly("build.gradle", "settings.gradle");
        assertThat(methods.value()).extracting(SelectMethod::name).containsExactly("adds", "emptyTest");
    }

    @Test
    void suiteConfigurationAnnotationsAreInheritedBySubclassSuites() {
        assertThat(annotationOn(DerivedSuite.class, Suite.class).failIfNoTests()).isFalse();
        assertThat(annotationOn(DerivedSuite.class, IncludeEngines.class).value()).containsExactly("junit-jupiter");
        assertThat(annotationOn(DerivedSuite.class, IncludeTags.class).value()).containsExactly("inherited-fast");
        assertThat(annotationsByTypeOn(DerivedSuite.class, ConfigurationParameter.class))
                .extracting(ConfigurationParameter::key)
                .containsExactly("inherited.suite.parameter");
        assertThat(annotationsByTypeOn(DerivedSuite.class, SelectFile.class))
                .extracting(SelectFile::value)
                .containsExactly("inherited-suite.gradle");
        assertThat(readAnnotation(DerivedSuite.class, SuiteDisplayName.class)).isNull();
    }

    @Test
    void explicitlyDeclaredRepeatableContainersCanBeConsumedAsRepeatableAnnotations() {
        assertThat(annotationsByTypeOn(ContainerDeclaredSuite.class, ConfigurationParameter.class))
                .extracting(ConfigurationParameter::key)
                .containsExactly("container.first", "container.second");
        assertThat(annotationsByTypeOn(ContainerDeclaredSuite.class, ConfigurationParametersResource.class))
                .extracting(ConfigurationParametersResource::value)
                .containsExactly("container-defaults.properties", "container-overrides.properties");
        assertThat(annotationsByTypeOn(ContainerDeclaredSuite.class, Select.class))
                .extracting(select -> select.value()[0])
                .containsExactly("package:org.example.container", "class:org.example.ContainerFixture");
        assertThat(annotationsByTypeOn(ContainerDeclaredSuite.class, SelectClasspathResource.class))
                .extracting(SelectClasspathResource::value)
                .containsExactly("container-suite.properties", "container-engine.properties");
        assertThat(annotationsByTypeOn(ContainerDeclaredSuite.class, SelectFile.class))
                .extracting(SelectFile::value)
                .containsExactly("container-one.gradle", "container-two.gradle");
        assertThat(annotationsByTypeOn(ContainerDeclaredSuite.class, SelectMethod.class))
                .extracting(SelectMethod::name)
                .containsExactly("containerTest", "containerParameterizedTest");
    }

    @Test
    void defaultAttributeValuesAreStableForOptionalSuiteAnnotations() {
        Suite suite = annotationOn(DefaultedSuite.class, Suite.class);
        SelectClasses classes = annotationOn(DefaultedSuite.class, SelectClasses.class);
        SelectClasspathResource classpathResource = annotationOn(DefaultedSuite.class, SelectClasspathResource.class);
        SelectFile file = annotationOn(DefaultedSuite.class, SelectFile.class);
        SelectMethod method = annotationOn(DefaultedSuite.class, SelectMethod.class);

        assertThat(suite.failIfNoTests()).isTrue();
        assertThat(classes.value()).isEmpty();
        assertThat(classes.names()).isEmpty();
        assertThat(classpathResource.value()).isEqualTo("defaults.properties");
        assertThat(classpathResource.line()).isZero();
        assertThat(classpathResource.column()).isZero();
        assertThat(file.value()).isEqualTo("defaults.txt");
        assertThat(file.line()).isZero();
        assertThat(file.column()).isZero();
        assertThat(method.value()).isEmpty();
        assertThat(method.type()).isEqualTo(Class.class);
        assertThat(method.typeName()).isEmpty();
        assertThat(method.name()).isEmpty();
        assertThat(method.parameterTypes()).isEmpty();
        assertThat(method.parameterTypeNames()).isEmpty();
    }

    @Test
    void beforeAndAfterSuiteLifecycleAnnotationsCanBeAppliedToStaticMethods() throws NoSuchMethodException {
        Method beforeAllSuites = LifecycleFixture.class.getDeclaredMethod("beforeAllSuites");
        Method afterAllSuites = LifecycleFixture.class.getDeclaredMethod("afterAllSuites");

        assertThat(annotationOn(beforeAllSuites, BeforeSuite.class)).isNotNull();
        assertThat(annotationOn(afterAllSuites, AfterSuite.class)).isNotNull();
    }

    private static <A extends Annotation> A annotationOn(AnnotatedElement element, Class<A> annotationType) {
        A annotation = readAnnotation(element, annotationType);

        assertThat(annotation)
                .as("%s should be present on %s", annotationType.getSimpleName(), element)
                .isNotNull();
        return annotation;
    }

    private static <A extends Annotation> A[] annotationsByTypeOn(AnnotatedElement element, Class<A> annotationType) {
        return readAnnotationsByType(element, annotationType);
    }

    // Checkstyle: allow direct annotation access
    private static <A extends Annotation> A readAnnotation(AnnotatedElement element, Class<A> annotationType) {
        AnnotatedElement elementAnnotationAccess = element;
        return elementAnnotationAccess.getAnnotation(annotationType);
    }

    private static <A extends Annotation> A[] readAnnotationsByType(AnnotatedElement element, Class<A> annotationType) {
        AnnotatedElement elementAnnotationAccess = element;
        return elementAnnotationAccess.getAnnotationsByType(annotationType);
    }
    // Checkstyle: disallow direct annotation access

    @Suite(failIfNoTests = false)
    @SuiteDisplayName("Comprehensive suite API fixture")
    @UseTechnicalNames
    @DisableParentConfigurationParameters
    @ConfigurationParameter(key = "junit.jupiter.conditions.deactivate", value = "org.example.DisabledCondition")
    @ConfigurationParameter(key = "custom.suite.mode", value = "native-friendly")
    @ConfigurationParametersResource("junit-platform.properties")
    @ConfigurationParametersResource("suite-overrides.properties")
    @IncludeEngines({ "junit-jupiter", "custom-engine" })
    @ExcludeEngines("vintage")
    @IncludeTags({ "fast", "native" })
    @ExcludeTags("slow")
    @IncludePackages({ "org.example.included", "org.example.shared" })
    @ExcludePackages("org.example.excluded")
    @IncludeClassNamePatterns({ ".*Tests?", ".*Spec" })
    @ExcludeClassNamePatterns(".*IT")
    @SelectClasses(value = { SelectedFixture.class, AnotherSelectedFixture.class }, names = {
            "java.lang.String", "java.util.ArrayList" })
    @SelectPackages({ "org.example.tests", "org.example.moretests" })
    @SelectDirectories({ "src/test/java", "src/integrationTest/java" })
    @SelectModules({ "java.base", "java.logging" })
    @SelectUris({ "file:///tmp/suite-one", "classpath:/suite-two" })
    @Select("class:org.example.First")
    @Select("method:org.example.Second#test")
    @SelectClasspathResource(value = "junit-platform.properties", line = 7, column = 3)
    @SelectClasspathResource(value = "META-INF/services/org.junit.platform.engine.TestEngine", line = 1)
    @SelectFile(value = "build.gradle", line = 12, column = 4)
    @SelectFile("settings.gradle")
    @SelectMethod(value = "org.example.CalculatorTests#adds(int, int)", type = SelectedFixture.class,
            typeName = "org.example.CalculatorTests", name = "adds", parameterTypes = { int.class, int.class },
            parameterTypeNames = "int, int")
    @SelectMethod(type = AnotherSelectedFixture.class, name = "emptyTest")
    private static class ComprehensiveSuite {
    }

    @Suite
    @SelectClasses
    @SelectClasspathResource("defaults.properties")
    @SelectFile("defaults.txt")
    @SelectMethod
    private static class DefaultedSuite {
    }

    @Suite
    @ConfigurationParameters({
            @ConfigurationParameter(key = "container.first", value = "one"),
            @ConfigurationParameter(key = "container.second", value = "two") })
    @ConfigurationParametersResources({
            @ConfigurationParametersResource("container-defaults.properties"),
            @ConfigurationParametersResource("container-overrides.properties") })
    @Selects({ @Select("package:org.example.container"), @Select("class:org.example.ContainerFixture") })
    @SelectClasspathResources({
            @SelectClasspathResource(value = "container-suite.properties", line = 5),
            @SelectClasspathResource(value = "container-engine.properties", column = 2) })
    @SelectFiles({ @SelectFile("container-one.gradle"), @SelectFile(value = "container-two.gradle", line = 9) })
    @SelectMethods({
            @SelectMethod(type = SelectedFixture.class, name = "containerTest"),
            @SelectMethod(typeName = "org.example.ContainerFixture", name = "containerParameterizedTest",
                    parameterTypeNames = "java.lang.String") })
    private static class ContainerDeclaredSuite {
    }

    @Suite(failIfNoTests = false)
    @SuiteDisplayName("Base suite display name")
    @ConfigurationParameter(key = "inherited.suite.parameter", value = "enabled")
    @IncludeEngines("junit-jupiter")
    @IncludeTags("inherited-fast")
    @SelectFile("inherited-suite.gradle")
    private static class BaseSuite {
    }

    private static class DerivedSuite extends BaseSuite {
    }

    private static class LifecycleFixture {
        @BeforeSuite
        static void beforeAllSuites() {
        }

        @AfterSuite
        static void afterAllSuites() {
        }
    }

    private static class SelectedFixture {
    }

    private static class AnotherSelectedFixture {
    }
}
