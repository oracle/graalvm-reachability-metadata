/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kotest.kotest_framework_engine_jvm

import io.kotest.core.source.SourceRef
import io.kotest.core.spec.style.FunSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val DISABLE_SOURCE_REF_PROPERTY = "kotest.framework.sourceref.disable"

public class SourceRefExampleSpec : FunSpec() {
    fun registerRootTest(): Unit {
        test("records source ref for a root test") {}
    }
}

public class SourceRefKtTest {
    @Test
    fun capturesSourceReferenceWhenRegisteringRootTest(): Unit {
        val previousDisableSourceRef: String? = System.clearProperty(DISABLE_SOURCE_REF_PROPERTY)

        try {
            val spec: SourceRefExampleSpec = SourceRefExampleSpec()
            spec.registerRootTest()

            val sourceRef: SourceRef = spec.rootTests().single().source

            assertThat(sourceRef).isInstanceOf(SourceRef.ClassLineSource::class.java)
            val classLineSource: SourceRef.ClassLineSource = sourceRef as SourceRef.ClassLineSource
            assertThat(classLineSource.fqn).isEqualTo(SourceRefExampleSpec::class.java.name)
            assertThat(classLineSource.lineNumber).isNotNull()
            assertThat(classLineSource.lineNumber!!).isPositive()
        } finally {
            if (previousDisableSourceRef != null) {
                System.setProperty(DISABLE_SOURCE_REF_PROPERTY, previousDisableSourceRef)
            }
        }
    }
}
