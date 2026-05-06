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

public class _UtilJvmKtTest {
    @Test
    public fun readFieldOrNullReadsPrivateFieldValue() {
        val holder: FieldHolder = FieldHolder("expected value")

        val value: CharSequence? = readFieldOrNull(holder, CharSequence::class.java, "token")

        assertThat(value).isEqualTo("expected value")
    }

    private class FieldHolder(
        private val token: String,
    )
}
