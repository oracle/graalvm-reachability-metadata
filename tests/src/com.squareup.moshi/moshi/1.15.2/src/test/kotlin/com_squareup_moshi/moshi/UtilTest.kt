/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.internal.Util
import java.lang.reflect.Constructor
import java.lang.reflect.Type
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class UtilTest {
    private val moshi: Moshi = Moshi.Builder().build()

    @Test
    public fun usesGeneratedAdapterConstructorWithMoshiAndTypeArrayForParameterizedTypes() {
        val type: Type = Types.newParameterizedType(
            GenericMoshiCase::class.java,
            String::class.java,
        )
        val adapter: JsonAdapter<GenericMoshiCase<String>> = moshi.adapter(type)

        val json: String = adapter.toJson(GenericMoshiCase("alpha"))

        assertThat(json).isEqualTo("""{"value":"alpha","adapter":"moshiTypeArray"}""")
    }

    @Test
    public fun usesGeneratedAdapterConstructorWithTypeArrayForParameterizedTypes() {
        val type: Type = Types.newParameterizedType(
            GenericTypeArrayOnlyCase::class.java,
            String::class.java,
        )
        val adapter: JsonAdapter<GenericTypeArrayOnlyCase<String>> = moshi.adapter(type)

        val json: String = adapter.toJson(GenericTypeArrayOnlyCase("bravo"))

        assertThat(json).isEqualTo("""{"adapter":"typeArray","type":"java.lang.String"}""")
    }

    @Test
    public fun usesGeneratedAdapterConstructorWithMoshiForConcreteTypes() {
        val adapter: JsonAdapter<ConcreteMoshiCase> = moshi.adapter(ConcreteMoshiCase::class.java)

        val json: String = adapter.toJson(ConcreteMoshiCase("charlie"))

        assertThat(json).isEqualTo("""{"name":"charlie","adapter":"moshi"}""")
    }

    @Test
    public fun usesGeneratedAdapterNoArgConstructorForConcreteTypes() {
        val adapter: JsonAdapter<ConcreteNoArgCase> = moshi.adapter(ConcreteNoArgCase::class.java)

        val json: String = adapter.toJson(ConcreteNoArgCase("delta"))

        assertThat(json).isEqualTo("""{"name":"delta","adapter":"noArg"}""")
    }

    @Test
    public fun looksUpKotlinDefaultsConstructor() {
        val constructor: Constructor<DefaultsConstructorCase> =
            Util.lookupDefaultsConstructor(DefaultsConstructorCase::class.java)

        assertThat(constructor).isNotNull()
    }
}

@JsonClass(generateAdapter = true)
public data class GenericMoshiCase<T>(val value: T)

public class GenericMoshiCaseJsonAdapter<T>(
    moshi: Moshi,
    private val typeArguments: Array<Type>,
) : JsonAdapter<GenericMoshiCase<T>>() {
    private val valueAdapter: JsonAdapter<T> = moshi.adapter(typeArguments[0])

    override fun fromJson(reader: JsonReader): GenericMoshiCase<T>? {
        reader.beginObject()
        var result: T? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "value" -> result = valueAdapter.fromJson(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        @Suppress("UNCHECKED_CAST")
        return GenericMoshiCase(result as T)
    }

    override fun toJson(writer: JsonWriter, value: GenericMoshiCase<T>?) {
        writer.beginObject()
        writer.name("value")
        valueAdapter.toJson(writer, value!!.value)
        writer.name("adapter").value("moshiTypeArray")
        writer.endObject()
    }
}

@JsonClass(generateAdapter = true)
public data class GenericTypeArrayOnlyCase<T>(val value: T)

public class GenericTypeArrayOnlyCaseJsonAdapter<T>(
    private val typeArguments: Array<Type>,
) : JsonAdapter<GenericTypeArrayOnlyCase<T>>() {
    override fun fromJson(reader: JsonReader): GenericTypeArrayOnlyCase<T>? {
        throw UnsupportedOperationException("fromJson is not used by this test adapter")
    }

    override fun toJson(writer: JsonWriter, value: GenericTypeArrayOnlyCase<T>?) {
        writer.beginObject()
        writer.name("adapter").value("typeArray")
        writer.name("type").value(typeArguments[0].typeName)
        writer.endObject()
    }
}

@JsonClass(generateAdapter = true)
public data class ConcreteMoshiCase(val name: String)

public class ConcreteMoshiCaseJsonAdapter(
    moshi: Moshi,
) : JsonAdapter<ConcreteMoshiCase>() {
    private val stringAdapter: JsonAdapter<String> = moshi.adapter(String::class.java)

    override fun fromJson(reader: JsonReader): ConcreteMoshiCase? {
        reader.beginObject()
        var name: String = ""
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "name" -> name = stringAdapter.fromJson(reader).orEmpty()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return ConcreteMoshiCase(name)
    }

    override fun toJson(writer: JsonWriter, value: ConcreteMoshiCase?) {
        writer.beginObject()
        writer.name("name")
        stringAdapter.toJson(writer, value!!.name)
        writer.name("adapter").value("moshi")
        writer.endObject()
    }
}

@JsonClass(generateAdapter = true)
public data class ConcreteNoArgCase(val name: String)

public class ConcreteNoArgCaseJsonAdapter : JsonAdapter<ConcreteNoArgCase>() {
    override fun fromJson(reader: JsonReader): ConcreteNoArgCase? {
        reader.beginObject()
        var name: String = ""
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "name" -> name = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return ConcreteNoArgCase(name)
    }

    override fun toJson(writer: JsonWriter, value: ConcreteNoArgCase?) {
        writer.beginObject()
        writer.name("name").value(value!!.name)
        writer.name("adapter").value("noArg")
        writer.endObject()
    }
}

public data class DefaultsConstructorCase(val name: String = "echo")
