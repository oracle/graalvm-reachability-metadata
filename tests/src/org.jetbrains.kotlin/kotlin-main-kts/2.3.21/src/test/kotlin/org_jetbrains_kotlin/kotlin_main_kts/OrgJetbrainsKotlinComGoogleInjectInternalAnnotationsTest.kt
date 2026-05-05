/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_main_kts

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.com.google.inject.internal.Annotations
import org.junit.jupiter.api.Test

public class OrgJetbrainsKotlinComGoogleInjectInternalAnnotationsTest {
    @Test
    public fun recognizesMarkerAndDefaultedAnnotationTypes() {
        assertThat(Annotations.isMarker(GeneratedMarkerAnnotation::class.java)).isTrue()
        assertThat(Annotations.isMarker(GeneratedDefaultedAnnotation::class.java)).isFalse()

        assertThat(Annotations.isAllDefaultMethods(GeneratedDefaultedAnnotation::class.java)).isTrue()
        assertThat(Annotations.isAllDefaultMethods(GeneratedRequiredMemberAnnotation::class.java)).isFalse()
    }

    @Test
    public fun generatedAnnotationProxyUsesDefaultMembersForObjectMethods() {
        val generated: GeneratedDefaultedAnnotation = Annotations.generateAnnotation(
            GeneratedDefaultedAnnotation::class.java,
        )
        val generatedAgain: GeneratedDefaultedAnnotation = Annotations.generateAnnotation(
            GeneratedDefaultedAnnotation::class.java,
        )

        assertThat(generated.name).isEqualTo("main-kts")
        assertThat(generated.priority).isEqualTo(21)

        assertThat(generated.toString())
            .contains("@${GeneratedDefaultedAnnotation::class.java.name}")
            .contains("name=")
            .contains("main-kts")
            .contains("priority=21")
        assertThat(generated.hashCode()).isEqualTo(generatedAgain.hashCode())
        assertThat(generated.equals(generatedAgain)).isTrue()
        assertThat(generated.equals("not an annotation")).isFalse()
    }
}

@Retention(AnnotationRetention.RUNTIME)
public annotation class GeneratedMarkerAnnotation

@Retention(AnnotationRetention.RUNTIME)
public annotation class GeneratedDefaultedAnnotation(
    val name: String = "main-kts",
    val priority: Int = 21,
)

@Retention(AnnotationRetention.RUNTIME)
public annotation class GeneratedRequiredMemberAnnotation(
    val required: String,
    val optional: String = "fallback",
)
