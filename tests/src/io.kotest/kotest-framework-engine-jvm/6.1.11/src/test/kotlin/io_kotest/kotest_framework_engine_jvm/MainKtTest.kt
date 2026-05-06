/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kotest.kotest_framework_engine_jvm

import io.kotest.engine.launcher.LauncherArgs
import io.kotest.engine.launcher.main
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val MISSING_SPEC_FQN = "io_kotest.kotest_framework_engine_jvm.MissingLauncherSpec"

public class MainKtTest {
    @Test
    fun resolvesSpecArgumentClassNamesBeforeLaunchingEngine(): Unit {
        val exception: ClassNotFoundException = assertThrows<ClassNotFoundException> {
            main(arrayOf("--${LauncherArgs.ARG_SPECS}", MISSING_SPEC_FQN))
        }

        assertThat(exception.message).contains(MISSING_SPEC_FQN)
    }
}
