/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:JvmName("ValueClassUnboxKeySerializerFixtures")

package com_fasterxml_jackson_module.jackson_module_kotlin

import kotlin.jvm.JvmInline

fun boxedUnboxKeyExample(value: String): Any = UnboxKeyExample(value)

@JvmInline
value class UnboxKeyExample(val value: String)
