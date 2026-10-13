/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_netflix_graphql_dgs.graphql_dgs

import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DefaultInputObjectMapperTest {
    private val mapper = DefaultInputObjectMapper()

    @Test
    fun mapsInputToJavaBean() {
        val input = mapper.mapToJavaObject(
            mapOf("name" to "Ada", "age" to 37),
            JavaInput::class.java,
        )

        assertThat(input.name).isEqualTo("Ada")
        assertThat(input.age).isEqualTo(37)
    }

    @Test
    fun mapsInputToJavaRecord() {
        val input = mapper.mapToJavaObject(
            mapOf("name" to "Ada", "age" to 37),
            JavaRecordInput::class.java,
        )

        assertThat(input.name).isEqualTo("Ada")
        assertThat(input.age).isEqualTo(37)
    }
}

class JavaInput {
    var name: String = ""
    var age: Int = 0
}

@JvmRecord
data class JavaRecordInput(
    val name: String,
    val age: Int,
)
