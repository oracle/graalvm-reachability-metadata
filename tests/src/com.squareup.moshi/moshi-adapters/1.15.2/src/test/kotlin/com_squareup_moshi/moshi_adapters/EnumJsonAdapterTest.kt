/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi_adapters

import com.squareup.moshi.Json
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.adapters.EnumJsonAdapter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class EnumJsonAdapterTest {
    enum class TransportMode {
        @Json(name = "bike-share")
        BICYCLE,
        TRAIN,
    }

    @Test
    fun readsAndWritesEnumConstantsUsingJsonNames(): Unit {
        val adapter: EnumJsonAdapter<TransportMode> = EnumJsonAdapter.create(TransportMode::class.java)

        assertThat(adapter.fromJson("\"bike-share\"")).isEqualTo(TransportMode.BICYCLE)
        assertThat(adapter.fromJson("\"TRAIN\"")).isEqualTo(TransportMode.TRAIN)
        assertThat(adapter.toJson(TransportMode.BICYCLE)).isEqualTo("\"bike-share\"")
        assertThat(adapter.toString()).isEqualTo("EnumJsonAdapter(${TransportMode::class.java.name})")
    }

    @Test
    fun returnsFallbackForUnknownEnumName(): Unit {
        val adapter: EnumJsonAdapter<TransportMode> = EnumJsonAdapter
            .create(TransportMode::class.java)
            .withUnknownFallback(TransportMode.TRAIN)

        assertThat(adapter.fromJson("\"subway\"")).isEqualTo(TransportMode.TRAIN)
    }

    @Test
    fun rejectsUnknownEnumNameWithoutFallback(): Unit {
        val adapter: EnumJsonAdapter<TransportMode> = EnumJsonAdapter.create(TransportMode::class.java)

        assertThatThrownBy { adapter.fromJson("\"subway\"") }
            .isInstanceOf(JsonDataException::class.java)
            .hasMessage("Expected one of [bike-share, TRAIN] but was subway at path \$")
    }
}
