/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.retrofit;

import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.GET;

import static org.assertj.core.api.Assertions.assertThat;

public class RetrofitAnonymous1Test {
    @Test
    void delegatesObjectMethodsOnServiceProxyToInvocationHandler() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://example.com/")
                .build();

        ProxyObjectMethodService service = retrofit.create(ProxyObjectMethodService.class);

        assertThat(service.toString()).isNotBlank();
    }

    public interface ProxyObjectMethodService {
        @GET("messages")
        Call<ResponseBody> messages();
    }
}
