/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_script_runtime;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

import kotlin.script.dependencies.ScriptContents;
import kotlin.script.experimental.dependencies.DependenciesResolver;
import kotlin.script.experimental.dependencies.DependenciesResolverKt;
import kotlin.script.experimental.dependencies.ScriptDependencies;
import kotlin.script.experimental.dependencies.ScriptReport;
import kotlin.script.extensions.SamWithReceiverAnnotations;
import kotlin.script.templates.AcceptedAnnotations;
import kotlin.script.templates.ScriptTemplateDefinition;
import kotlin.script.templates.standard.ScriptTemplateWithArgs;
import kotlin.script.templates.standard.ScriptTemplateWithBindings;
import kotlin.script.templates.standard.SimpleScriptTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Kotlin_script_runtimeTest {

    @Test
    void standardScriptTemplatesExposeConstructionInputs() {
        String[] args = new String[] {"alpha", "beta"};
        Map<String, Object> bindings = Map.of("answer", 42, "message", "hello");

        ArgsTemplate argsTemplate = new ArgsTemplate(args);
        BindingsTemplate bindingsTemplate = new BindingsTemplate(bindings);
        PlainTemplate plainTemplate = new PlainTemplate();

        assertThat(argsTemplate.getArgs()).containsExactly("alpha", "beta");
        assertThat(bindingsTemplate.getBindings())
                .containsEntry("answer", 42)
                .containsEntry("message", "hello");
        assertThat(plainTemplate).isNotNull();
    }

    @Test
    void scriptDependenciesBehaveAsStableValueObjects() {
        File javaHome = new File("java-home");
        List<File> classpath = List.of(new File("libs/runtime.jar"), new File("libs/api.jar"));
        List<String> imports = List.of("sample.runtime.*", "sample.api.Helper");
        List<File> sources = List.of(new File("src/main/kotlin/App.main.kts"));
        List<File> scripts = List.of(new File("scripts/bootstrap.main.kts"));

        ScriptDependencies dependencies = new ScriptDependencies(javaHome, classpath, imports, sources, scripts);
        ScriptDependencies copiedDependencies = dependencies.copy(
                javaHome,
                classpath,
                List.of("sample.runtime.*", "sample.api.Helper", "sample.extra.Extension"),
                sources,
                scripts
        );
        ScriptDependencies emptyDependencies = new ScriptDependencies();

        assertThat(dependencies.getJavaHome()).isEqualTo(javaHome);
        assertThat(dependencies.getClasspath()).containsExactlyElementsOf(classpath);
        assertThat(dependencies.getImports()).containsExactlyElementsOf(imports);
        assertThat(dependencies.getSources()).containsExactlyElementsOf(sources);
        assertThat(dependencies.getScripts()).containsExactlyElementsOf(scripts);
        assertThat(dependencies.component1()).isEqualTo(javaHome);
        assertThat(dependencies.component2()).containsExactlyElementsOf(classpath);
        assertThat(dependencies.component3()).containsExactlyElementsOf(imports);
        assertThat(dependencies.component4()).containsExactlyElementsOf(sources);
        assertThat(dependencies.component5()).containsExactlyElementsOf(scripts);
        assertThat(copiedDependencies.getImports())
                .containsExactly("sample.runtime.*", "sample.api.Helper", "sample.extra.Extension");
        assertThat(copiedDependencies).isNotEqualTo(dependencies);
        assertThat(emptyDependencies.getJavaHome()).isNull();
        assertThat(emptyDependencies.getClasspath()).isEmpty();
        assertThat(emptyDependencies.getImports()).isEmpty();
        assertThat(emptyDependencies.getSources()).isEmpty();
        assertThat(emptyDependencies.getScripts()).isEmpty();
    }

    @Test
    void scriptReportsAndResolverResultsPreserveState() {
        ScriptReport.Position position = new ScriptReport.Position(3, 5, 3, 17);
        ScriptReport report = new ScriptReport("missing import", ScriptReport.Severity.WARNING, position);
        ScriptReport updatedReport = report.copy("fixed import", ScriptReport.Severity.INFO, position.copy(4, 1, 4, 10));
        ScriptDependencies dependencies = new ScriptDependencies(
                new File("java-home"),
                List.of(new File("libs/runtime.jar")),
                List.of("sample.runtime.*"),
                List.of(new File("src/main/kotlin/App.main.kts")),
                List.of(new File("scripts/bootstrap.main.kts"))
        );
        DependenciesResolver.ResolveResult.Success success =
                new DependenciesResolver.ResolveResult.Success(dependencies, List.of(report));
        DependenciesResolver.ResolveResult.Failure failure =
                new DependenciesResolver.ResolveResult.Failure(report);

        assertThat(report.getMessage()).isEqualTo("missing import");
        assertThat(report.getSeverity()).isEqualTo(ScriptReport.Severity.WARNING);
        assertThat(report.getPosition()).isEqualTo(position);
        assertThat(report.component1()).isEqualTo("missing import");
        assertThat(report.component2()).isEqualTo(ScriptReport.Severity.WARNING);
        assertThat(report.component3()).isEqualTo(position);
        assertThat(position.getStartLine()).isEqualTo(3);
        assertThat(position.getStartColumn()).isEqualTo(5);
        assertThat(position.getEndLine()).isEqualTo(3);
        assertThat(position.getEndColumn()).isEqualTo(17);
        assertThat(updatedReport.getMessage()).isEqualTo("fixed import");
        assertThat(updatedReport.getSeverity()).isEqualTo(ScriptReport.Severity.INFO);
        assertThat(updatedReport.getPosition().getStartLine()).isEqualTo(4);
        assertThat(updatedReport.getPosition().getEndColumn()).isEqualTo(10);

        assertThat(success.getDependencies()).isEqualTo(dependencies);
        assertThat(success.getReports()).containsExactly(report);
        assertThat(success.component1()).isEqualTo(dependencies);
        assertThat(success.component2()).containsExactly(report);
        assertThat(success.copy(dependencies, List.of(report))).isEqualTo(success);

        assertThat(failure.getDependencies()).isNull();
        assertThat(failure.getReports()).containsExactly(report);
        assertThat(failure.component1()).containsExactly(report);
        assertThat(failure.copy(List.of(report))).isEqualTo(failure);
    }

    @Test
    void scriptReportSeveritiesAndEmptyDependenciesRemainAccessible() {
        assertThat(ScriptReport.Severity.values())
                .containsExactly(
                        ScriptReport.Severity.FATAL,
                        ScriptReport.Severity.ERROR,
                        ScriptReport.Severity.WARNING,
                        ScriptReport.Severity.INFO,
                        ScriptReport.Severity.DEBUG
                );
        assertThat(ScriptReport.Severity.valueOf("WARNING")).isEqualTo(ScriptReport.Severity.WARNING);
        assertThat(ScriptDependencies.Companion.getEmpty()).isEqualTo(new ScriptDependencies());
    }

    @Test
    void builtInDependencyResolversProduceSuccessfulResultsWithoutReports() {
        ScriptContents scriptContents = new TestScriptContents(
                new File("scripts/sample.main.kts"),
                "println(\"hello\")"
        );
        DependenciesResolver.ResolveResult.Success noDependenciesResult =
                DependenciesResolver.NoDependencies.INSTANCE.resolve(scriptContents, Map.of());
        ScriptDependencies dependencies = new ScriptDependencies(
                new File("java-home"),
                List.of(new File("libs/runtime.jar")),
                List.of("sample.runtime.*"),
                List.of(new File("src/main/kotlin/App.main.kts")),
                List.of(new File("scripts/bootstrap.main.kts"))
        );
        DependenciesResolver.ResolveResult.Success helperResult = DependenciesResolverKt.asSuccess(dependencies);

        assertThat(noDependenciesResult.getDependencies()).isEqualTo(ScriptDependencies.Companion.getEmpty());
        assertThat(noDependenciesResult.getReports()).isEmpty();
        assertThat(helperResult.getDependencies()).isSameAs(dependencies);
        assertThat(helperResult.getReports()).isEmpty();
    }

    @Test
    void scriptTemplateDefinitionAnnotationsExposeDefaultsAndOverrides() {
        ScriptTemplateDefinition defaultDefinition = DefaultAnnotatedTemplate.class.getAnnotationsByType(ScriptTemplateDefinition.class)[0];
        ScriptTemplateDefinition customDefinition = CustomAnnotatedTemplate.class.getAnnotationsByType(ScriptTemplateDefinition.class)[0];

        assertThat(defaultDefinition).isNotNull();
        assertThat(defaultDefinition.resolver()).isEqualTo(DependenciesResolver.NoDependencies.class);
        assertThat(defaultDefinition.scriptFilePattern()).isEqualTo(".*\\.kts");

        assertThat(customDefinition).isNotNull();
        assertThat(customDefinition.resolver()).isEqualTo(TestDependenciesResolver.class);
        assertThat(customDefinition.scriptFilePattern()).isEqualTo(".*\\.main\\.kts");
    }

    @Test
    void scriptTemplateRuntimeAnnotationsRemainAccessible() throws NoSuchMethodException {
        AcceptedAnnotations classAcceptedAnnotations =
                CustomAnnotatedTemplate.class.getAnnotationsByType(AcceptedAnnotations.class)[0];
        SamWithReceiverAnnotations samWithReceiverAnnotations =
                CustomAnnotatedTemplate.class.getAnnotationsByType(SamWithReceiverAnnotations.class)[0];
        AcceptedAnnotations methodAcceptedAnnotations = CustomAnnotatedTemplate.class
                .getDeclaredMethod("acceptsSupportedMarkers")
                .getAnnotationsByType(AcceptedAnnotations.class)[0];

        assertThat(classAcceptedAnnotations).isNotNull();
        assertThat(classAcceptedAnnotations.supportedAnnotationClasses()).containsExactly(SupportedMarker.class);

        assertThat(samWithReceiverAnnotations).isNotNull();
        assertThat(samWithReceiverAnnotations.annotations())
                .containsExactly("sample.FirstReceiver", "sample.SecondReceiver");

        assertThat(methodAcceptedAnnotations).isNotNull();
        assertThat(methodAcceptedAnnotations.supportedAnnotationClasses())
                .containsExactly(SupportedMarker.class, AnotherSupportedMarker.class);
    }

    private static final class ArgsTemplate extends ScriptTemplateWithArgs {

        private ArgsTemplate(String[] args) {
            super(args);
        }
    }

    private static final class BindingsTemplate extends ScriptTemplateWithBindings {

        private BindingsTemplate(Map<String, Object> bindings) {
            super(bindings);
        }
    }

    private static final class PlainTemplate extends SimpleScriptTemplate {
    }

    @ScriptTemplateDefinition
    private static final class DefaultAnnotatedTemplate {
    }

    @ScriptTemplateDefinition(resolver = TestDependenciesResolver.class, scriptFilePattern = ".*\\.main\\.kts")
    @AcceptedAnnotations(supportedAnnotationClasses = SupportedMarker.class)
    @SamWithReceiverAnnotations(annotations = {"sample.FirstReceiver", "sample.SecondReceiver"})
    private static final class CustomAnnotatedTemplate {

        @AcceptedAnnotations(supportedAnnotationClasses = {SupportedMarker.class, AnotherSupportedMarker.class})
        private void acceptsSupportedMarkers() {
        }
    }

    private @interface SupportedMarker {
    }

    private @interface AnotherSupportedMarker {
    }

    private static final class TestDependenciesResolver implements DependenciesResolver {

        @Override
        public ResolveResult resolve(ScriptContents scriptContents, Map<String, ? extends Object> environment) {
            return DependenciesResolver.NoDependencies.INSTANCE.resolve(scriptContents, environment);
        }
    }

    private static final class TestScriptContents implements ScriptContents {

        private final File file;
        private final String text;

        private TestScriptContents(File file, String text) {
            this.file = file;
            this.text = text;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public Iterable<Annotation> getAnnotations() {
            return List.of();
        }

        @Override
        public CharSequence getText() {
            return text;
        }
    }

}
