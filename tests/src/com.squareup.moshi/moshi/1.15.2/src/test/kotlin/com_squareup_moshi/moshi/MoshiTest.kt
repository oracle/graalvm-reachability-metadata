/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import java.lang.reflect.Type
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class MoshiTest {
    @Test
    public fun usesQualifierSpecificAdapterRegisteredByType(): Unit {
        val moshi: Moshi = Moshi.Builder()
            .add(
                String::class.java as Type,
                ReversedQualifier::class.java,
                ReversingStringJsonAdapter,
            )
            .build()

        val qualifiedAdapter: JsonAdapter<String> = moshi.adapter(
            String::class.java,
            ReversedQualifier::class.java,
        )
        val unqualifiedAdapter: JsonAdapter<String> = moshi.adapter(String::class.java)

        assertThat(qualifiedAdapter.fromJson("\"ihsom\"")).isEqualTo("moshi")
        assertThat(qualifiedAdapter.toJson("moshi")).isEqualTo("\"ihsom\"")
        assertThat(unqualifiedAdapter.fromJson("\"plain\"")).isEqualTo("plain")
    }
}

@JsonQualifier
@Retention(AnnotationRetention.RUNTIME)
public annotation class ReversedQualifier

public object ReversingStringJsonAdapter : JsonAdapter<String>() {
    override fun fromJson(reader: JsonReader): String? {
        return reader.nextString().reversed()
    }

    override fun toJson(writer: JsonWriter, value: String?): Unit {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value.reversed())
        }
    }
}
