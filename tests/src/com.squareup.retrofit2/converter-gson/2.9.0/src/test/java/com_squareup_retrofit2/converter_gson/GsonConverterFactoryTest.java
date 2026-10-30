/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.converter_gson;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.lang.annotation.Annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GsonConverterFactoryTest {
    @Test
    void usesConfiguredGsonForBothRequestAndResponseConversion() throws Exception {
        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .serializeNulls()
                .create();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://localhost/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        Converter<Account, RequestBody> requestConverter = retrofit.requestBodyConverter(
                Account.class, new Annotation[0], new Annotation[0]);
        Account account = new Account();
        account.displayName = "reader";
        RequestBody request = requestConverter.convert(account);
        Buffer buffer = new Buffer();
        request.writeTo(buffer);

        assertThat(buffer.readUtf8()).isEqualTo("""
                {"display_name":"reader","contact_email":null}""");

        Converter<ResponseBody, Account> responseConverter = retrofit.responseBodyConverter(
                Account.class, new Annotation[0]);
        try (ResponseBody body = ResponseBody.create(MediaType.get("application/json"), """
                {"display_name":"writer","contact_email":"writer@example.test"}
                """)) {
            Account decoded = responseConverter.convert(body);

            assertThat(decoded.displayName).isEqualTo("writer");
            assertThat(decoded.contactEmail).isEqualTo("writer@example.test");
        }
    }

    @Test
    void rejectsNullGsonConfiguration() {
        assertThrows(NullPointerException.class, () -> GsonConverterFactory.create(null));
    }

    public static class Account {
        public String displayName;
        public String contactEmail;
    }
}
