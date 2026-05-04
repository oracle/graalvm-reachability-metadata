/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_thauvin_erik_urlencoder.urlencoder_lib_jvm

import net.thauvin.erik.urlencoder.UrlEncoderUtil
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

public class Urlencoder_lib_jvmTest {
    @Test
    fun encodesAsciiCharactersUsingLibraryDefaultSafeSet(): Unit {
        val source: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~!*'();:@&=+\u0000"
        val encoded: String = UrlEncoderUtil.encode(source)

        assertThat(encoded).isEqualTo(
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._%7E%21%2A%27%28%29%3B%3A%40%26%3D%2B%00",
        )
    }

    @Test
    fun preservesExplicitlyAllowedUrlDelimiters(): Unit {
        val url: String = "https://example.test/a path?q=hello world&flag=true#section-1"
        val allowedDelimiters: String = ":/?&=#"

        val encoded: String = UrlEncoderUtil.encode(url, allow = allowedDelimiters)

        assertThat(encoded).isEqualTo("https://example.test/a%20path?q=hello%20world&flag=true#section-1")
    }

    @Test
    fun preservesExplicitlyAllowedNonAsciiCharacters(): Unit {
        val source: String = "café 🦕/茶"
        val allowedCharacters: String = "é🦕"

        val encoded: String = UrlEncoderUtil.encode(source, allow = allowedCharacters)

        assertThat(encoded).isEqualTo("café%20🦕%2F%E8%8C%B6")
    }

    @Test
    fun encodesSpacesAsPlusOnlyWhenRequested(): Unit {
        val source: String = "a b+c"

        assertThat(UrlEncoderUtil.encode(source)).isEqualTo("a%20b%2Bc")
        assertThat(UrlEncoderUtil.encode(source, spaceToPlus = true)).isEqualTo("a+b%2Bc")
        assertThat(UrlEncoderUtil.encode(source, allow = "+", spaceToPlus = true)).isEqualTo("a+b+c")
    }

    @Test
    fun encodesUtf8ForBmpAndSupplementaryCodePoints(): Unit {
        val source: String = "café € 🦕"
        val encoded: String = UrlEncoderUtil.encode(source)

        assertThat(encoded).isEqualTo("caf%C3%A9%20%E2%82%AC%20%F0%9F%A6%95")
        assertThat(UrlEncoderUtil.decode(encoded)).isEqualTo(source)
    }

    @Test
    fun encodesLiteralPercentSignsInsteadOfPreservingEscapeSequences(): Unit {
        val source: String = "already%20encoded%GG literal%"
        val encoded: String = UrlEncoderUtil.encode(source)

        assertThat(encoded).isEqualTo("already%2520encoded%25GG%20literal%25")
        assertThat(UrlEncoderUtil.decode(encoded)).isEqualTo(source)
    }

    @Test
    fun returnsEmptyAndAlreadySafeInputsUnchanged(): Unit {
        val empty: String = ""
        val safe: String = "Alpha-09._beta"

        assertThat(UrlEncoderUtil.encode(empty)).isSameAs(empty)
        assertThat(UrlEncoderUtil.decode(empty)).isSameAs(empty)
        assertThat(UrlEncoderUtil.encode(safe)).isSameAs(safe)
        assertThat(UrlEncoderUtil.decode(safe)).isSameAs(safe)
    }

    @Test
    fun decodesPercentEscapesAndLeavesPlusSignsByDefault(): Unit {
        val encoded: String = "first%20second+third%2Bfourth%2ffifth%C3%A9"
        val decoded: String = UrlEncoderUtil.decode(encoded)

        assertThat(decoded).isEqualTo("first second+third+fourth/fifthé")
    }

    @Test
    fun decodesPlusAsSpaceWhenRequested(): Unit {
        val formEncoded: String = "query=hello+world%2Bencoded&empty="

        assertThat(UrlEncoderUtil.decode(formEncoded)).isEqualTo("query=hello+world+encoded&empty=")
        assertThat(UrlEncoderUtil.decode(formEncoded, plusToSpace = true)).isEqualTo("query=hello world+encoded&empty=")
    }

    @Test
    fun decodesConsecutiveUtf8ByteSequencesBeforeContinuingWithPlainText(): Unit {
        val encoded: String = "%F0%9F%A6%96-%E6%97%A5%D1%84text%21"

        assertThat(UrlEncoderUtil.decode(encoded)).isEqualTo("🦖-日фtext!")
    }

    @Test
    fun roundTripsCommonUrlComponentValues(): Unit {
        val values: List<String> = listOf(
            "simple",
            "path segment/with delimiter",
            "email+tag@example.test",
            "symbols: !@#$%^&*()[]{}",
            "mixed languages: café 日本 Ελληνικά",
            "supplementary planes: 🦕🦖",
        )

        values.forEach { value: String ->
            val encoded: String = UrlEncoderUtil.encode(value)
            assertThat(UrlEncoderUtil.decode(encoded))
                .describedAs("round-trip for %s through %s", value, encoded)
                .isEqualTo(value)
        }
    }

    @Test
    fun rejectsIncompleteTrailingPercentEscapes(): Unit {
        assertThatIllegalArgumentException()
            .isThrownBy { UrlEncoderUtil.decode("abc%") }
            .withMessageContaining("Incomplete trailing escape")

        assertThatIllegalArgumentException()
            .isThrownBy { UrlEncoderUtil.decode("abc%A") }
            .withMessageContaining("Incomplete trailing escape")
    }

    @Test
    fun rejectsPercentEscapesWithNonHexCharacters(): Unit {
        assertThatIllegalArgumentException()
            .isThrownBy { UrlEncoderUtil.decode("abc%GGdef") }
            .withMessageContaining("Illegal characters in escape sequence")
            .withCauseInstanceOf(NumberFormatException::class.java)
    }
}
