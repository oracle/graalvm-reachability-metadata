/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.converter_gson;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.lang.annotation.Annotation;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class GsonResponseBodyConverterTest {
    @Test
    void deserializesGenericListOfAnnotatedObjects() throws Exception {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://localhost/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        Converter<ResponseBody, List<Product>> converter = retrofit.responseBodyConverter(
                new TypeToken<List<Product>>() { }.getType(), new Annotation[0]);
        try (ResponseBody body = ResponseBody.create(MediaType.get("application/json"), """
                [
                  {"product_name":"caf\u00e9","quantity":3,"extra":"ignored"},
                  {"product_name":"tea","quantity":7}
                ]
                """)) {
            List<Product> products = converter.convert(body);

            assertThat(products).hasSize(2);
            assertThat(products.get(0).name).isEqualTo("caf\u00e9");
            assertThat(products.get(0).quantity).isEqualTo(3);
            assertThat(products.get(1).name).isEqualTo("tea");
            assertThat(products.get(1).quantity).isEqualTo(7);
        }
    }

    @Test
    void deserializesJsonNull() throws Exception {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://localhost/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        Converter<ResponseBody, Product> converter = retrofit.responseBodyConverter(
                Product.class, new Annotation[0]);
        try (ResponseBody body = ResponseBody.create(MediaType.get("application/json"), "null")) {
            assertThat(converter.convert(body)).isNull();
        }
    }

    public static class Product {
        @SerializedName("product_name")
        public String name;
        public int quantity;
    }
}
