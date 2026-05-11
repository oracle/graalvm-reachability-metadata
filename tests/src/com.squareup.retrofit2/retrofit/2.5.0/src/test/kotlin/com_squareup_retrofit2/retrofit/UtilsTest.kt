/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.retrofit

import okhttp3.ResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

public class UtilsTest {
    public interface GenericArrayQueryService {
        @GET("search")
        public fun search(@Query("tag") tags: Array<List<String>>): Call<ResponseBody>
    }

    @Test
    public fun resolvesRawTypeForGenericArrayQueryParameter(): Unit {
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://example.com/")
            .validateEagerly(true)
            .build()

        val service: GenericArrayQueryService = retrofit.create(GenericArrayQueryService::class.java)
        val call: Call<ResponseBody> = service.search(
            arrayOf(
                listOf("red", "blue"),
                listOf("green")
            )
        )

        assertThat(call.request().url().queryParameterValues("tag"))
            .containsExactly("[red, blue]", "[green]")
    }
}
