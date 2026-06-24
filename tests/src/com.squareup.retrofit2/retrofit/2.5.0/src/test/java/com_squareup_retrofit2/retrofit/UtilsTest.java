/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.retrofit;

import java.util.List;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.GET;
import retrofit2.http.Query;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsTest {
    @Test
    void validatesGenericArrayParameterType() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://example.com/")
                .validateEagerly(true)
                .build();

        GenericArrayQueryService service = retrofit.create(GenericArrayQueryService.class);

        assertThat(service).isInstanceOf(GenericArrayQueryService.class);
    }

    public interface GenericArrayQueryService {
        @GET("messages")
        Call<ResponseBody> messages(@Query("tag") List<String>[] tags);
    }
}
