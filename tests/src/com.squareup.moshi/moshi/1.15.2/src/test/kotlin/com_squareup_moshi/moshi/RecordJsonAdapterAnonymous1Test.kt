/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class RecordJsonAdapterAnonymous1Test {
    private val moshi: Moshi = Moshi.Builder().build()

    @Test
    public fun createsAdapterForJvmRecordAndRoundTripsJson(): Unit {
        val adapter: JsonAdapter<RecordPayload> = moshi.adapter(RecordPayload::class.java)

        val encoded: String = adapter.toJson(RecordPayload("moshi", 3, true))
        val decoded: RecordPayload? = adapter.fromJson(
            """{"name":"moshi","count":3,"enabled":true,"unknown":"ignored"}""",
        )

        assertThat(encoded).isEqualTo("""{"name":"moshi","count":3,"enabled":true}""")
        assertThat(decoded).isEqualTo(RecordPayload("moshi", 3, true))
    }
}

@JvmRecord
public data class RecordPayload(
    public val name: String,
    public val count: Int,
    public val enabled: Boolean,
)
