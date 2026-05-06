/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kotest.kotest_framework_engine_jvm

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.engine.config.KotestEngineProperties
import io.kotest.engine.config.ProjectConfigLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val SPEC_FQN = "io_kotest.kotest_framework_engine_jvm.ExampleSpec"

public class ProjectConfig : AbstractProjectConfig() {
    override val failOnEmptyTestSuite: Boolean = true
}

public class ProjectConfigLoaderTest {
    @Test
    fun loadsProjectConfigDiscoveredFromSpecPackage(): Unit {
        val previousConfigFqn: String? = System.clearProperty(KotestEngineProperties.PROJECT_CONFIGURATION_FQN)

        try {
            val projectConfig: AbstractProjectConfig? = ProjectConfigLoader.load(setOf(SPEC_FQN))

            assertThat(projectConfig is ProjectConfig).isTrue()
            assertThat(projectConfig?.failOnEmptyTestSuite).isTrue()
        } finally {
            if (previousConfigFqn != null) {
                System.setProperty(KotestEngineProperties.PROJECT_CONFIGURATION_FQN, previousConfigFqn)
            }
        }
    }
}
