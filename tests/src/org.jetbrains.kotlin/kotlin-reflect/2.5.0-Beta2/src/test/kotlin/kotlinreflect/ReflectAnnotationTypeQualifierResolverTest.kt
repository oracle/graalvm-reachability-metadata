package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredMemberFunctions

class ReflectAnnotationTypeQualifierResolverTest {
    @Test
    fun enhancesAlwaysNonnullReturnAndParameterTypes() {
        val function = ReflectJavaFixtures.QualifiedStrings::class.declaredMemberFunctions
            .single { it.name == "required" }
        val parameter = function.parameters.single { it.kind == KParameter.Kind.VALUE }

        assertThat(function.returnType.classifier).isEqualTo(String::class)
        assertThat(function.returnType.toString()).isEqualTo("kotlin.String")
        assertThat(parameter.type.toString()).isEqualTo("kotlin.String")
        assertThat(function.call(ReflectJavaFixtures.QualifiedStrings(), "value"))
            .isEqualTo("required:value")
    }

    @Test
    fun enhancesMaybeAndNeverQualifiersToNullableTypes() {
        val functions = ReflectJavaFixtures.QualifiedStrings::class.declaredMemberFunctions
            .associateBy { it.name }
        val optional = functions.getValue("optional")
        val parameter = optional.parameters.single { it.kind == KParameter.Kind.VALUE }
        val absent = functions.getValue("absent")
        val fixture = ReflectJavaFixtures.QualifiedStrings()

        assertThat(optional.returnType.isMarkedNullable).isTrue()
        assertThat(parameter.type.isMarkedNullable).isTrue()
        assertThat(absent.returnType.isMarkedNullable).isTrue()
        assertThat(optional.call(fixture, null)).isNull()
        assertThat(optional.call(fixture, "present")).isEqualTo("present")
        assertThat(absent.call(fixture)).isNull()
    }

    @Test
    fun preservesPlatformFlexibilityForUnknownQualifier() {
        val function = ReflectJavaFixtures.QualifiedStrings::class.declaredMemberFunctions
            .single { it.name == "platform" }

        assertThat(function.returnType.classifier).isEqualTo(String::class)
        assertThat(function.returnType.toString()).isEqualTo("kotlin.String!")
        assertThat(function.call(ReflectJavaFixtures.QualifiedStrings())).isEqualTo("platform")
    }
}
