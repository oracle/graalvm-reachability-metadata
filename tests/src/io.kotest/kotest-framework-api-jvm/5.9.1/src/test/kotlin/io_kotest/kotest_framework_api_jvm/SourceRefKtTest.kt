/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kotest.kotest_framework_api_jvm

import io.kotest.core.source.SourceRef
import io.kotest.core.source.sourceRef
import io.kotest.core.spec.style.AnnotationSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class SourceRefKtTest {
    @Test
    fun `locates the enclosing spec at the current execution point`(): Unit {
        val source: SourceRef = SourceCapturingSpec().captureSource()

        assertThat(source).isInstanceOf(SourceRef.ClassSource::class.java)
        val classSource: SourceRef.ClassSource = source as SourceRef.ClassSource
        assertThat(classSource.fqn).isEqualTo(SourceCapturingSpec::class.java.name)
    }

    public class SourceCapturingSpec : AnnotationSpec() {
        fun captureSource(): SourceRef = sourceRef()
    }
}
