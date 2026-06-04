/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.jvm.JvmInline

@JvmInline
value class ReflectionCacheInlineValue(val value: String)

class ReflectionCacheValueClassHolder(
    @param:JsonProperty("wrapped")
    @get:JsonProperty("wrapped")
    val wrapped: ReflectionCacheInlineValue
) {
    fun wrappedValue(): String = wrapped.value
}
