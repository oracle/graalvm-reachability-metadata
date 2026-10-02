/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_common

import kotlin.reflect.KClass
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.commandLineArgs
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.displayName
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.host.GetScriptingClass
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate
import kotlin.script.experimental.host.createEvaluationConfigurationFromTemplate
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.host.getEvaluationContext
import kotlin.script.experimental.host.getScriptingClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class ConfigurationFromTemplateKtTest {
    @Test
    fun createsDefinitionFromTemplateUsingConstructorBackedConfigurations(): Unit {
        val definition = createScriptDefinitionFromTemplate(
            KotlinType(ConstructorBackedScriptTemplate::class),
            scriptLoadingHostConfiguration(),
        )

        assertThat(definition.compilationConfiguration[ScriptCompilationConfiguration.displayName])
            .isEqualTo("constructor backed script")
        assertThat(definition.compilationConfiguration[ScriptCompilationConfiguration.fileExtension])
            .isEqualTo("constructor.kts")
        assertThat(definition.compilationConfiguration[ScriptCompilationConfiguration.defaultImports])
            .containsExactly("java.time.Instant")
        assertThat(definition.evaluationConfiguration[ScriptEvaluationConfiguration.constructorArgs])
            .containsExactly("evaluation constructor argument")

        val compilationHost = definition.compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]
        assertThat(compilationHost?.evaluationCommandLineArgs()).containsExactly("from base aware host")
    }

    @Test
    fun createsCompilationConfigurationUsingDefaultConstructedHostConfiguration(): Unit {
        val configuration = createCompilationConfigurationFromTemplate(
            KotlinType(DefaultConstructedHostScriptTemplate::class),
            scriptLoadingHostConfiguration(),
        )

        assertThat(configuration[ScriptCompilationConfiguration.fileExtension]).isEqualTo("default-host.kts")
        assertThat(configuration[ScriptCompilationConfiguration.hostConfiguration]?.evaluationCommandLineArgs())
            .containsExactly("from default constructed host")
    }

    @Test
    fun createsEvaluationConfigurationUsingConstructorBackedEvaluationConfiguration(): Unit {
        val configuration = createEvaluationConfigurationFromTemplate(
            KotlinType(ConstructorBackedScriptTemplate::class),
            scriptLoadingHostConfiguration(),
        )

        assertThat(configuration[ScriptEvaluationConfiguration.constructorArgs])
            .containsExactly("evaluation constructor argument")
        assertThat(configuration[ScriptEvaluationConfiguration.hostConfiguration]?.evaluationCommandLineArgs())
            .containsExactly("from base aware host")
    }

    private fun scriptLoadingHostConfiguration(): ScriptingHostConfiguration = ScriptingHostConfiguration {
        ScriptingHostConfiguration.getScriptingClass.put(
            object : GetScriptingClass {
                override fun invoke(
                    classType: KotlinType,
                    contextClass: KClass<*>,
                    hostConfiguration: ScriptingHostConfiguration,
                ): KClass<*> = requireNotNull(classType.fromClass) {
                    "Test templates are supplied as reflected Kotlin types"
                }
            },
        )
    }

    private fun ScriptingHostConfiguration.evaluationCommandLineArgs(): List<String>? {
        val contextData = get(ScriptingHostConfiguration.getEvaluationContext)?.invoke(this)
        return contextData?.get(kotlin.script.experimental.api.ScriptEvaluationContextData.commandLineArgs)
    }
}

@KotlinScript(
    displayName = "constructor backed script",
    fileExtension = "constructor.kts",
    compilationConfiguration = ConstructorBackedCompilationConfiguration::class,
    evaluationConfiguration = ConstructorBackedEvaluationConfiguration::class,
    hostConfiguration = BaseAwareHostConfiguration::class,
)
public abstract class ConstructorBackedScriptTemplate

@KotlinScript(
    displayName = "default constructed host script",
    fileExtension = "default-host.kts",
    compilationConfiguration = ConstructorBackedCompilationConfiguration::class,
    hostConfiguration = DefaultConstructedHostConfiguration::class,
)
public abstract class DefaultConstructedHostScriptTemplate

public class ConstructorBackedCompilationConfiguration : ScriptCompilationConfiguration({
    defaultImports("java.time.Instant")
})

public class ConstructorBackedEvaluationConfiguration : ScriptEvaluationConfiguration({
    constructorArgs("evaluation constructor argument")
})

public class BaseAwareHostConfiguration(baseConfiguration: ScriptingHostConfiguration) : ScriptingHostConfiguration(
    baseConfiguration,
    body = {
        ScriptingHostConfiguration.getEvaluationContext.put {
            kotlin.script.experimental.api.ScriptEvaluationContextData {
                commandLineArgs("from base aware host")
            }
        }
    },
)

public class DefaultConstructedHostConfiguration : ScriptingHostConfiguration({
    ScriptingHostConfiguration.getEvaluationContext.put {
        kotlin.script.experimental.api.ScriptEvaluationContextData {
            commandLineArgs("from default constructed host")
        }
    }
})
