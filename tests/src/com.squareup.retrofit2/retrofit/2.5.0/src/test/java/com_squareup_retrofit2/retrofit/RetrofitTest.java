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

public class RetrofitTest {
    @Test
    void createsProxyForAnnotatedServiceInterface() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://example.com/")
                .build();

        SimpleService service = retrofit.create(SimpleService.class);

        assertThat(service).isInstanceOf(SimpleService.class);
    }

    @Test
    void eagerlyValidatesDeclaredServiceMethods() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://example.com/")
                .validateEagerly(true)
                .build();

        EagerService service = retrofit.create(EagerService.class);

        assertThat(service).isInstanceOf(EagerService.class);
    }

    public interface SimpleService {
        @GET("messages")
        Call<ResponseBody> messages();
    }

    public interface EagerService {
        @GET("users")
        Call<ResponseBody> users();
    }
}
