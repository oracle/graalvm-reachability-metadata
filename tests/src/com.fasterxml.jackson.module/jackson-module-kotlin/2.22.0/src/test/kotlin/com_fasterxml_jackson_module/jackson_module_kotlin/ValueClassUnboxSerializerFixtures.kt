/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:JvmName("ValueClassUnboxSerializerFixtures")

package com_fasterxml_jackson_module.jackson_module_kotlin

import kotlin.jvm.JvmInline

fun boxedUnboxExample(value: String): Any = UnboxExample(value)

fun boxedNullableUnboxExample(value: String?): Any = NullableUnboxExample(value)

@JvmInline
value class UnboxExample(val value: String)

@JvmInline
value class NullableUnboxExample(val value: String?)
