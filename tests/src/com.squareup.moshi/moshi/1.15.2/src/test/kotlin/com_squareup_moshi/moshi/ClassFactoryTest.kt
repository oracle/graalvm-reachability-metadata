/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class ClassFactoryTest {
    private val moshi: Moshi = Moshi.Builder().build()

    @Test
    public fun createsAdapterForConcreteJavaTypeWithoutNoArgConstructor(): Unit {
        val adapter: JsonAdapter<JsonEncodingException> = moshi.adapter(JsonEncodingException::class.java)

        val json: String = adapter.toJson(JsonEncodingException("bad encoding"))

        assertThat(json).isEqualTo("{}")
    }
}
