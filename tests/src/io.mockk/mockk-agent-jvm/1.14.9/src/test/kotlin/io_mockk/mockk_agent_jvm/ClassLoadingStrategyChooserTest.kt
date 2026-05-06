/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_agent_jvm

import io.mockk.proxy.jvm.ClassLoadingStrategyChooser
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

public class ClassLoadingStrategyChooserTest {
    @Test
    @Timeout(60)
    fun choosesClassLoadingStrategyForApplicationClass(): Unit {
        val strategy: ClassLoadingStrategy<ClassLoader> = try {
            ClassLoadingStrategyChooser.chooseClassLoadingStrategy(SampleApplicationClass::class.java)
        } catch (throwable: Throwable) {
            if (!MockkNativeImageSupport.isExpectedNativeImageFailure(throwable)) {
                throw throwable
            }
            return
        }

        assertThat(strategy).isNotNull()
    }

    private class SampleApplicationClass
}
