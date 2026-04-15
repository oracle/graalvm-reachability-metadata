/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:JvmName("ValueClassSerializerStaticJsonValueFixtures")

package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonValue
import kotlin.jvm.JvmInline

fun boxedStaticJsonValueExample(value: String): Any = StaticJsonValueExample(value)

@JvmInline
value class StaticJsonValueExample(val value: String) {
    companion object {
        @JvmStatic
        @JsonValue
        fun jsonValue(value: String): String? = if (value == "emit-null") null else "json:$value"
    }
}
