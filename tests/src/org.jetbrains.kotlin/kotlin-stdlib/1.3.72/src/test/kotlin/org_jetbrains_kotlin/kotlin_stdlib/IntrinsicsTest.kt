/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org_jetbrains_kotlin.kotlin_stdlib

import kotlin.jvm.internal.Intrinsics
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class IntrinsicsTest {
    @Test
    public fun checkHasClassAcceptsPresentClassFromInternalName() {
        assertThatCode { Intrinsics.checkHasClass(presentInternalName()) }
            .doesNotThrowAnyException()
    }

    @Test
    public fun checkHasClassWithRequiredVersionAcceptsPresentClassFromInternalName() {
        assertThatCode { Intrinsics.checkHasClass(presentInternalName(), REQUIRED_RUNTIME) }
            .doesNotThrowAnyException()
    }

    @Test
    public fun checkHasClassReportsMissingClassFromInternalName() {
        assertThatThrownBy { Intrinsics.checkHasClass(MISSING_INTERNAL_NAME) }
            .isInstanceOf(ClassNotFoundException::class.java)
            .hasMessageContaining("Class $MISSING_FQ_NAME is not found")
            .hasMessageContaining("Please update the Kotlin runtime to the latest version")
            .hasCauseInstanceOf(ClassNotFoundException::class.java)
    }

    @Test
    public fun checkHasClassWithRequiredVersionReportsMissingRuntimeClass() {
        assertThatThrownBy { Intrinsics.checkHasClass(MISSING_INTERNAL_NAME, REQUIRED_RUNTIME) }
            .isInstanceOf(ClassNotFoundException::class.java)
            .hasMessageContaining("Class $MISSING_FQ_NAME is not found")
            .hasMessageContaining("requires the Kotlin runtime of version at least $REQUIRED_RUNTIME")
            .hasCauseInstanceOf(ClassNotFoundException::class.java)
    }

    private companion object {
        private val presentInternalNameParts: List<String> = listOf(
            "org_jetbrains_kotlin",
            "kotlin_stdlib",
            "IntrinsicsTest"
        )

        private fun presentInternalName(): String = presentInternalNameParts.joinToString("/")

        private const val MISSING_INTERNAL_NAME = "org/jetbrains/kotlin/stdlib/test/DefinitelyMissingRuntimeClass"
        private const val MISSING_FQ_NAME = "org.jetbrains.kotlin.stdlib.test.DefinitelyMissingRuntimeClass"
        private const val REQUIRED_RUNTIME = "a newer runtime"
    }
}
