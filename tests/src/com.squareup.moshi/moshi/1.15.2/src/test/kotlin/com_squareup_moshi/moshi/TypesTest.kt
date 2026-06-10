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
import com.squareup.moshi.Types
import java.lang.reflect.Type
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class TypesTest {
    @Test
    public fun returnsRawTypeForGenericArrayTypes(): Unit {
        val arrayType: Type = Types.arrayOf(String::class.java)

        val rawType: Class<*> = Types.getRawType(arrayType)

        assertThat(rawType.isArray).isTrue()
        assertThat(rawType.componentType).isEqualTo(String::class.java)
    }

    @Test
    @Suppress("DEPRECATION")
    public fun readsJsonQualifierAnnotationsFromFields(): Unit {
        val annotations: Set<Annotation> = Types.getFieldJsonQualifierAnnotations(
            QualifiedFieldHolder::class.java,
            "name",
        )

        assertThat(annotations).hasSize(1)
        assertThat(annotations.single().annotationClass.java)
            .isEqualTo(UppercaseQualifier::class.java)
    }

    @Test
    public fun createsRuntimeJsonQualifierImplementationForAdapterLookup(): Unit {
        val moshi: Moshi = Moshi.Builder()
            .add(UppercaseStringAdapterFactory())
            .build()

        val adapter: JsonAdapter<String> = moshi.adapter(
            String::class.java,
            UppercaseQualifier::class.java,
        )

        assertThat(adapter.fromJson("\"moshi\"")).isEqualTo("MOSHI")
        assertThat(adapter.toJson("moshi")).isEqualTo("\"MOSHI\"")
    }
}

public class QualifiedFieldHolder {
    @field:UppercaseQualifier
    public val name: String = "moshi"
}

@JsonQualifier
@Retention(AnnotationRetention.RUNTIME)
public annotation class UppercaseQualifier

public class UppercaseStringAdapterFactory : JsonAdapter.Factory {
    override fun create(
        type: Type,
        annotations: Set<Annotation>,
        moshi: Moshi,
    ): JsonAdapter<*>? {
        if (type != String::class.java) {
            return null
        }
        val remainingAnnotations: Set<Annotation> = Types.nextAnnotations(
            annotations,
            UppercaseQualifier::class.java,
        ) ?: return null
        if (remainingAnnotations.isNotEmpty()) {
            return null
        }
        return UppercaseStringAdapter
    }
}

public object UppercaseStringAdapter : JsonAdapter<String>() {
    override fun fromJson(reader: JsonReader): String? {
        return reader.nextString().uppercase()
    }

    override fun toJson(writer: JsonWriter, value: String?): Unit {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value.uppercase())
        }
    }
}
