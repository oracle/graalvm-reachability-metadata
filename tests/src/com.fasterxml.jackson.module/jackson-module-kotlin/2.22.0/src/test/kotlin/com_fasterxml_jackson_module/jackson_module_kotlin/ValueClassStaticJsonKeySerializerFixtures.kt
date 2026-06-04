/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:JvmName("ValueClassStaticJsonKeySerializerFixtures")

package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonKey
import kotlin.jvm.JvmInline

fun boxedStaticJsonKeyExample(value: String): Any = StaticJsonKeyExample(value)

@JvmInline
value class StaticJsonKeyExample(val value: String) {
    companion object {
        @JvmStatic
        @JsonKey
        fun jsonKey(value: String): String = "json-key:$value"
    }
}
