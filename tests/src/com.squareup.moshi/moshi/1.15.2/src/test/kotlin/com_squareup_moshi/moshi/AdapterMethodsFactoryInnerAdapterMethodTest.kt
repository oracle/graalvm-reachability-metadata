/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class AdapterMethodsFactoryInnerAdapterMethodTest {
    private val moshi: Moshi = Moshi.Builder()
        .add(StreamingDistanceAdapter())
        .build()

    @Test
    public fun invokesToJsonAdapterMethodWithWriterValueAndDelegateAdapter(): Unit {
        val adapter: JsonAdapter<StreamingDistance> = moshi.adapter(StreamingDistance::class.java)

        val json: String = adapter.toJson(StreamingDistance(42))

        assertThat(json).isEqualTo("""{"meters":42}""")
    }

    @Test
    public fun invokesFromJsonAdapterMethodWithReaderAndDelegateAdapter(): Unit {
        val adapter: JsonAdapter<StreamingDistance> = moshi.adapter(StreamingDistance::class.java)

        val decoded: StreamingDistance? = adapter.fromJson("""{"meters":7,"unit":"m"}""")

        assertThat(decoded).isEqualTo(StreamingDistance(7))
    }
}

public data class StreamingDistance(public val meters: Int)

public class StreamingDistanceAdapter {
    @ToJson
    public fun toJson(
        writer: JsonWriter,
        value: StreamingDistance,
        intAdapter: JsonAdapter<Int>,
    ): Unit {
        writer.beginObject()
        writer.name("meters")
        intAdapter.toJson(writer, value.meters)
        writer.endObject()
    }

    @FromJson
    public fun fromJson(
        reader: JsonReader,
        intAdapter: JsonAdapter<Int>,
    ): StreamingDistance {
        reader.beginObject()
        var meters: Int = 0
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "meters" -> meters = intAdapter.fromJson(reader) ?: 0
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return StreamingDistance(meters)
    }
}
