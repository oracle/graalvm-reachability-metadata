/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kotest.kotest_framework_engine_jvm

import io.kotest.engine.config.KotestPropertiesLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val KOTEST_PROPERTIES_FILENAME_PROPERTY = "kotest.properties.filename"
private const val TEST_PROPERTIES_RESOURCE = "/kotest-loader-test.properties"
private const val LOADED_SYSTEM_PROPERTY = "io.kotest.framework.engine.jvm.loader.test.value"

public class KotestPropertiesLoaderTest {
    @Test
    fun loadsSystemPropertiesFromConfiguredClasspathResource(): Unit {
        val previousPropertiesFilename: String? = System.getProperty(KOTEST_PROPERTIES_FILENAME_PROPERTY)
        val previousLoadedValue: String? = System.getProperty(LOADED_SYSTEM_PROPERTY)

        try {
            System.setProperty(KOTEST_PROPERTIES_FILENAME_PROPERTY, TEST_PROPERTIES_RESOURCE)
            System.clearProperty(LOADED_SYSTEM_PROPERTY)

            KotestPropertiesLoader.loadAndApplySystemPropsFile()

            assertThat(System.getProperty(LOADED_SYSTEM_PROPERTY)).isEqualTo("loaded-from-resource")
        } finally {
            restoreSystemProperty(KOTEST_PROPERTIES_FILENAME_PROPERTY, previousPropertiesFilename)
            restoreSystemProperty(LOADED_SYSTEM_PROPERTY, previousLoadedValue)
        }
    }
}

private fun restoreSystemProperty(name: String, value: String?): Unit {
    if (value == null) {
        System.clearProperty(name)
    } else {
        System.setProperty(name, value)
    }
}
