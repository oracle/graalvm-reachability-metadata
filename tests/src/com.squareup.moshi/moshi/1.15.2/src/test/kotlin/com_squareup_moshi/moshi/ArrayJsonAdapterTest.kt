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
import org.junit.jupiter.api.Test

public class ArrayJsonAdapterTest {
    @Test
    public fun decodesObjectArrayFromJsonArray(): Unit {
        val moshi: Moshi = Moshi.Builder().build()
        val adapter: JsonAdapter<Array<String>> = moshi.adapter(Array<String>::class.java)

        val decoded: Array<String>? = adapter.fromJson("[\"red\",\"green\",\"blue\"]")

        assertThat(decoded).containsExactly("red", "green", "blue")
    }
}
