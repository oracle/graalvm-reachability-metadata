/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class StandardJsonAdaptersInnerEnumJsonAdapterTest {
    @Test
    public fun readsAndWritesEnumConstantsUsingJsonNames(): Unit {
        val adapter: JsonAdapter<CompassDirection> = Moshi.Builder()
            .build()
            .adapter(CompassDirection::class.java)

        assertThat(adapter.fromJson("\"n\"")).isEqualTo(CompassDirection.NORTH)
        assertThat(adapter.fromJson("\"south\"")).isEqualTo(CompassDirection.SOUTH)
        assertThat(adapter.toJson(CompassDirection.NORTH)).isEqualTo("\"n\"")
        assertThat(adapter.toJson(CompassDirection.EAST)).isEqualTo("\"EAST\"")
    }
}

public enum class CompassDirection {
    @Json(name = "n")
    NORTH,

    @Json(name = "south")
    SOUTH,

    EAST,
}
