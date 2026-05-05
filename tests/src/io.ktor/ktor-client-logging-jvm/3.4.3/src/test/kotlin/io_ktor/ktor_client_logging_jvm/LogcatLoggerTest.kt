/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_logging_jvm

import android.util.Log
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.Logger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

public class LogcatLoggerTest {
    @BeforeEach
    public fun clearLogcatMessages(): Unit {
        Log.clear()
    }

    @Test
    public fun androidLoggerWritesShortMessagesToLogcat(): Unit {
        val message: String = "message sent through android.util.Log"

        Logger.ANDROID.log(message)

        assertThat(Log.messages()).containsExactly(Log.Message("Ktor Client", message))
    }
}
