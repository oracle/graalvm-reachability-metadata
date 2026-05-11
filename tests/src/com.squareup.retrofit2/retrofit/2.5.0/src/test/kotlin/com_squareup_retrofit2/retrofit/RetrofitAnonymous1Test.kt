/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.retrofit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET

public class RetrofitAnonymous1Test {
    public interface ObjectMethodService {
        @GET("users")
        public fun users(): Call<Void>
    }

    @Test
    public fun proxyObjectMethodDelegatesToInvocationHandlerObjectMethod(): Unit {
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://example.com/")
            .build()

        val service: ObjectMethodService = retrofit.create(ObjectMethodService::class.java)
        val proxyDescription: String = service.toString()

        assertThat(proxyDescription).isNotBlank()
    }
}
