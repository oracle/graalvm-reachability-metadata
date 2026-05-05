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
    @Test
    fun adapterMethodsInvokeOneAndTwoArgumentMethods(): Unit {
        val moshi: Moshi = Moshi.Builder()
            .add(AdapterMethodCoverageAdapter())
            .build()
        val oneArgumentAdapter: JsonAdapter<OneArgumentValue> = moshi.adapter(OneArgumentValue::class.java)
        val twoArgumentAdapter: JsonAdapter<TwoArgumentValue> = moshi.adapter(TwoArgumentValue::class.java)

        assertThat(oneArgumentAdapter.toJson(OneArgumentValue("alpha")))
            .isEqualTo("\"one-alpha\"")
        assertThat(oneArgumentAdapter.fromJson("\"one-beta\""))
            .isEqualTo(OneArgumentValue("beta"))
        assertThat(twoArgumentAdapter.toJson(TwoArgumentValue("gamma")))
            .isEqualTo("""{"encoded":"two-gamma"}""")
        assertThat(twoArgumentAdapter.fromJson("""{"encoded":"two-delta"}"""))
            .isEqualTo(TwoArgumentValue("delta"))
    }
}

public data class OneArgumentValue(public val name: String)

public data class TwoArgumentValue(public val name: String)

public class AdapterMethodCoverageAdapter {
    @ToJson
    public fun oneArgumentValueToJson(value: OneArgumentValue): String {
        return "one-${value.name}"
    }

    @FromJson
    public fun oneArgumentValueFromJson(value: String): OneArgumentValue {
        return OneArgumentValue(value.removePrefix("one-"))
    }

    @ToJson
    public fun twoArgumentValueToJson(writer: JsonWriter, value: TwoArgumentValue): Unit {
        writer.beginObject()
        writer.name("encoded")
        writer.value("two-${value.name}")
        writer.endObject()
    }

    @FromJson
    public fun twoArgumentValueFromJson(reader: JsonReader): TwoArgumentValue {
        reader.beginObject()
        val name: String = reader.nextName()
        assertThat(name).isEqualTo("encoded")
        val encodedValue: String = reader.nextString()
        reader.endObject()
        return TwoArgumentValue(encodedValue.removePrefix("two-"))
    }
}
