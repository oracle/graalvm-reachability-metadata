/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com_squareup_okhttp3.okhttp_jvm

import okhttp3.internal.readFieldOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class Okhttp3InternalUtilJvmKtTest {
    @Test
    public fun readsDeclaredFieldValue(): Unit {
        val holder: UtilJvmFieldHolder = UtilJvmFieldHolder("okhttp")

        val value: String? = readFieldOrNull(holder, String::class.java, "value")

        assertThat(value).isEqualTo("okhttp")
    }

    @Test
    public fun readsFieldValueFromDelegate(): Unit {
        val holder: UtilJvmDelegateHolder = UtilJvmDelegateHolder(UtilJvmFieldHolder("delegated"))

        val value: String? = readFieldOrNull(holder, String::class.java, "value")

        assertThat(value).isEqualTo("delegated")
    }

    @Test
    public fun returnsNullWhenDeclaredFieldHasDifferentType(): Unit {
        val holder: UtilJvmNumberHolder = UtilJvmNumberHolder(5)

        val value: String? = readFieldOrNull(holder, String::class.java, "value")

        assertThat(value).isNull()
    }
}

private class UtilJvmFieldHolder(
    private val value: String,
)

private class UtilJvmDelegateHolder(
    private val delegate: Any,
)

private class UtilJvmNumberHolder(
    private val value: Int,
)
