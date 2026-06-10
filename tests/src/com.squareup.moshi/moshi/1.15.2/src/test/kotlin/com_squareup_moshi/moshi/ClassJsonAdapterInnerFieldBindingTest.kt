/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Index
import org.junit.jupiter.api.Test

public class ClassJsonAdapterInnerFieldBindingTest {
    private val moshi: Moshi = Moshi.Builder().build()

    @Test
    public fun readsAndWritesJavaObjectFieldsWithReflectiveClassAdapter(): Unit {
        val adapter: JsonAdapter<Index> = moshi.adapter(Index::class.java)

        val json: String = adapter.toJson(Index.atIndex(3))
        val decoded: Index? = adapter.fromJson("""{"value":5}""")

        assertThat(json).isEqualTo("""{"value":3}""")
        assertThat(decoded).isEqualTo(Index.atIndex(5))
    }
}
