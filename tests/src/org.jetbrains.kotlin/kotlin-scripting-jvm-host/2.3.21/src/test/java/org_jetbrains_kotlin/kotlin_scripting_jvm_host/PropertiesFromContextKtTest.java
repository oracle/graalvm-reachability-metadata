/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm_host;

import java.util.Map;
import javax.script.ScriptContext;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import kotlin.Unit;
import kotlin.script.experimental.api.KotlinType;
import kotlin.script.experimental.api.ResultWithDiagnostics;
import kotlin.script.experimental.api.ScriptCompilationConfiguration;
import kotlin.script.experimental.api.ScriptCompilationKt;
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext;
import kotlin.script.experimental.api.ScriptEvaluationConfiguration;
import kotlin.script.experimental.api.ScriptEvaluationConfigurationRefinementContext;
import kotlin.script.experimental.api.ScriptEvaluationKt;
import kotlin.script.experimental.host.StringScriptSource;
import kotlin.script.experimental.jvmhost.jsr223.Jsr223CompilationConfigurationBuilder;
import kotlin.script.experimental.jvmhost.jsr223.Jsr223EvaluationConfigurationBuilder;
import kotlin.script.experimental.jvmhost.jsr223.KotlinJsr223HostConfigurationKt;
import kotlin.script.experimental.jvmhost.jsr223.PropertiesFromContextKt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class PropertiesFromContextKtTest {
    @Test
    void importsBindingTypesWithEnginePrecedenceAndPreservesDeclaredTypes() {
        ScriptContext context = context();
        context.setAttribute("shared", "global", ScriptContext.GLOBAL_SCOPE);
        context.setAttribute("globalOnly", "available", ScriptContext.GLOBAL_SCOPE);
        context.setAttribute("shared", 42, ScriptContext.ENGINE_SCOPE);
        context.setAttribute("count", 7, ScriptContext.ENGINE_SCOPE);
        context.setAttribute("optional", null, ScriptContext.ENGINE_SCOPE);
        KotlinType declaredType = new KotlinType("kotlin.Number");
        ScriptCompilationConfiguration configuration = compilation(context, true, Map.of("count", declaredType));

        ScriptCompilationConfiguration refined = value(PropertiesFromContextKt.configureProvidedPropertiesFromJsr223Context(
                new ScriptConfigurationRefinementContext(new StringScriptSource("shared", "bindings.kts", null), configuration, null)));

        Map<String, KotlinType> properties = refined.get(ScriptCompilationKt.getProvidedProperties(ScriptCompilationConfiguration.Companion));
        assertThat(properties).containsOnlyKeys("shared", "globalOnly", "count", "optional");
        assertThat(properties.get("shared").getTypeName()).isEqualTo("kotlin.Int");
        assertThat(properties.get("globalOnly").getTypeName()).isEqualTo("kotlin.String");
        assertThat(properties.get("count")).isEqualTo(declaredType);
        assertThat(properties.get("optional").getTypeName()).isEqualTo("kotlin.Any");
        assertThat(properties.get("optional").isNullable()).isTrue();
        assertThat(configuration.get(ScriptCompilationKt.getProvidedProperties(ScriptCompilationConfiguration.Companion)))
                .containsOnlyKeys("count");
    }

    @Test
    void leavesCompilationConfigurationUnchangedWhenImportingBindingsIsDisabled() {
        ScriptContext context = context();
        context.setAttribute("unrequested", "value", ScriptContext.ENGINE_SCOPE);
        ScriptCompilationConfiguration configuration = compilation(context, false, Map.of());

        assertThat(value(PropertiesFromContextKt.configureProvidedPropertiesFromJsr223Context(
                new ScriptConfigurationRefinementContext(new StringScriptSource("1", "bindings.kts", null), configuration, null))))
                .isSameAs(configuration);
    }

    @Test
    void suppliesDeclaredBindingValuesWithoutOverwritingExplicitEvaluationProperties() throws Exception {
        ScriptContext context = context();
        context.setAttribute("shared", "global", ScriptContext.GLOBAL_SCOPE);
        context.setAttribute("globalOnly", "global value", ScriptContext.GLOBAL_SCOPE);
        context.setAttribute("shared", "engine", ScriptContext.ENGINE_SCOPE);
        context.setAttribute("explicit", "binding value", ScriptContext.ENGINE_SCOPE);
        context.setAttribute("optional", null, ScriptContext.ENGINE_SCOPE);
        ScriptCompilationConfiguration compilation = compilation(context, true, Map.of(
                "shared", new KotlinType("kotlin.String"), "globalOnly", new KotlinType("kotlin.String"),
                "explicit", new KotlinType("kotlin.String"), "optional", new KotlinType("kotlin.Any", true)));
        ScriptEvaluationConfiguration evaluation = new ScriptEvaluationConfiguration(builder -> {
            builder.put(KotlinJsr223HostConfigurationKt.getGetScriptContext(Jsr223EvaluationConfigurationBuilder.Companion), () -> context);
            builder.put(ScriptEvaluationKt.getProvidedProperties(ScriptEvaluationConfiguration.Companion), Map.of("explicit", "configured value"));
            return Unit.INSTANCE;
        });

        ScriptEvaluationConfiguration refined = value(PropertiesFromContextKt.configureProvidedPropertiesFromJsr223Context(
                new ScriptEvaluationConfigurationRefinementContext(ScriptFixtures.compiledScript(compilation), evaluation, null)));

        Map<String, Object> properties = refined.get(ScriptEvaluationKt.getProvidedProperties(ScriptEvaluationConfiguration.Companion));
        assertThat(properties).containsOnlyKeys("shared", "globalOnly", "explicit", "optional");
        assertThat(properties).containsEntry("shared", "engine").containsEntry("globalOnly", "global value")
                .containsEntry("explicit", "configured value").containsEntry("optional", null);
        assertThat(evaluation.get(ScriptEvaluationKt.getProvidedProperties(ScriptEvaluationConfiguration.Companion)))
                .containsOnlyKeys("explicit");
    }

    @Test
    void reportsADeclaredPropertyMissingFromBothBindingScopes() throws Exception {
        ScriptCompilationConfiguration compilation = compilation(context(), true, Map.of("required", new KotlinType("kotlin.String")));
        ScriptEvaluationConfiguration evaluation = new ScriptEvaluationConfiguration(builder -> {
            ScriptContext context = context();
            builder.put(KotlinJsr223HostConfigurationKt.getGetScriptContext(Jsr223EvaluationConfigurationBuilder.Companion), () -> context);
            return Unit.INSTANCE;
        });

        ResultWithDiagnostics<ScriptEvaluationConfiguration> result = PropertiesFromContextKt.configureProvidedPropertiesFromJsr223Context(
                new ScriptEvaluationConfigurationRefinementContext(ScriptFixtures.compiledScript(compilation), evaluation, null));

        assertThat(result).isInstanceOf(ResultWithDiagnostics.Failure.class);
        assertThat(result.getReports()).anySatisfy(report -> assertThat(report.getMessage()).contains("required", "not found in the bindings"));
    }

    private static ScriptContext context() {
        SimpleScriptContext context = new SimpleScriptContext();
        context.setBindings(new SimpleBindings(), ScriptContext.GLOBAL_SCOPE);
        return context;
    }

    private static ScriptCompilationConfiguration compilation(ScriptContext context, boolean importAll, Map<String, KotlinType> properties) {
        return new ScriptCompilationConfiguration(builder -> {
            builder.put(KotlinJsr223HostConfigurationKt.getGetScriptContext(Jsr223CompilationConfigurationBuilder.Companion), () -> context);
            builder.put(KotlinJsr223HostConfigurationKt.getImportAllBindings(Jsr223CompilationConfigurationBuilder.Companion), importAll);
            builder.put(ScriptCompilationKt.getProvidedProperties(ScriptCompilationConfiguration.Companion), properties);
            return Unit.INSTANCE;
        });
    }

    private static <T> T value(ResultWithDiagnostics<T> result) {
        assertThat(result).isInstanceOf(ResultWithDiagnostics.Success.class);
        return ((ResultWithDiagnostics.Success<T>) result).getValue();
    }
}
