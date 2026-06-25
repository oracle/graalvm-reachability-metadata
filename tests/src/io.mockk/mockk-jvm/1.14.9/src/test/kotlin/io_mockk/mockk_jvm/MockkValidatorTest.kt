/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.MockKException
import io.mockk.core.config.PropertiesLoader
import io.mockk.impl.restrict.MockkValidator
import io.mockk.impl.restrict.RestrictMockkConfiguration
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.ArrayList
import java.util.Properties

public class MockkValidatorTest {
    @Test
    fun rejectsClassAssignableToDefaultRestrictedType(): Unit {
        val properties: Properties = Properties().apply {
            setProperty("mockk.throwExceptionOnBadMock", "true")
        }
        val validator: MockkValidator = MockkValidator(
            RestrictMockkConfiguration(
                object : PropertiesLoader {
                    override fun loadProperties(): Properties = properties
                },
            ),
        )

        assertThatThrownBy { validator.validateMockableClass(ArrayList::class) }
            .isInstanceOf(MockKException::class.java)
            .hasMessage("Mocking java.util.ArrayList is not allowed!")
    }
}
