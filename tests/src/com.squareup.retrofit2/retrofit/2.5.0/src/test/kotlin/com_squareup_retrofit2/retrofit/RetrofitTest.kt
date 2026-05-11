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

public class RetrofitTest {
    public interface ExampleService {
        @GET("users")
        public fun users(): Call<ResponseBody>
    }

    @Test
    public fun createsServiceProxyAndEagerlyValidatesDeclaredMethods(): Unit {
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://example.com/")
            .validateEagerly(true)
            .build()

        val service: ExampleService = retrofit.create(ExampleService::class.java)
        val call: Call<ResponseBody> = service.users()

        assertThat(service).isInstanceOf(ExampleService::class.java)
        assertThat(call.request().url().toString()).isEqualTo("https://example.com/users")
    }
}
