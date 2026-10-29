/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.converter_gson;

import com.google.gson.annotations.SerializedName;
import okhttp3.RequestBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class GsonRequestBodyConverterTest {
    @Test
    void serializesAnnotatedObjectAsUtf8Json() throws Exception {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://localhost/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        Converter<Message, RequestBody> converter = retrofit.requestBodyConverter(
                Message.class, new Annotation[0], new Annotation[0]);
        Message message = new Message();
        message.text = "caf\u00e9";
        message.count = 4;

        RequestBody body = converter.convert(message);
        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        byte[] expected = "{\"message_text\":\"caf\u00e9\",\"count\":4}".getBytes(StandardCharsets.UTF_8);

        assertThat(body.contentType().toString()).isEqualTo("application/json; charset=UTF-8");
        assertThat(body.contentLength()).isEqualTo(expected.length);
        assertThat(buffer.readByteArray()).containsExactly(expected);
    }

    @Test
    void serializesNullAsJsonNull() throws Exception {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://localhost/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        Converter<Message, RequestBody> converter = retrofit.requestBodyConverter(
                Message.class, new Annotation[0], new Annotation[0]);
        RequestBody body = converter.convert(null);
        Buffer buffer = new Buffer();
        body.writeTo(buffer);

        assertThat(buffer.readUtf8()).isEqualTo("null");
    }

    public static class Message {
        @SerializedName("message_text")
        public String text;
        public int count;
    }
}
