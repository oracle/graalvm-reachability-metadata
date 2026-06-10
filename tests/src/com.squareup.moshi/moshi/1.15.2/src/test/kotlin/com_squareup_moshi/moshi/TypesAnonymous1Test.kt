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
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.lang.reflect.Type
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class TypesAnonymous1Test {
    @Test
    public fun delegatesUnhandledObjectMethodsFromRuntimeQualifierProxy(): Unit {
        RuntimeQualifierFactory.capturedAnnotation = null
        val moshi: Moshi = Moshi.Builder()
            .add(RuntimeQualifierFactory())
            .build()

        val adapter: JsonAdapter<String> = moshi.adapter(
            String::class.java,
            RuntimeQualifier::class.java,
        )
        assertThat(adapter).isNotNull()
        val annotation: Annotation = requireNotNull(RuntimeQualifierFactory.capturedAnnotation)
        val handler: java.lang.reflect.InvocationHandler = Proxy.getInvocationHandler(annotation)
        val getClassMethod: Method = Any::class.java.getMethod("getClass")

        val result: Any? = handler.invoke(annotation, getClassMethod, emptyArray<Any?>())

        assertThat(result).isEqualTo(annotation.javaClass)
    }
}

@JsonQualifier
@Retention(AnnotationRetention.RUNTIME)
public annotation class RuntimeQualifier

public class RuntimeQualifierFactory : JsonAdapter.Factory {
    public companion object {
        public var capturedAnnotation: Annotation? = null
    }

    override fun create(
        type: Type,
        annotations: Set<Annotation>,
        moshi: Moshi,
    ): JsonAdapter<*>? {
        if (type != String::class.java || annotations.size != 1) {
            return null
        }
        capturedAnnotation = annotations.single()
        return RuntimeQualifierStringAdapter
    }
}

public object RuntimeQualifierStringAdapter : JsonAdapter<String>() {
    override fun fromJson(reader: JsonReader): String? {
        return reader.nextString()
    }

    override fun toJson(writer: JsonWriter, value: String?): Unit {
        writer.value(value)
    }
}
