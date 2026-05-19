/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_http_converter;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.http.converter.autoconfigure.HttpMessageConverters;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class HttpMessageConvertersTest {

    @Test
    void createsConvertersFromAdditionalConverterCollection() {
        HttpMessageConverter<?> stringConverter = new StringHttpMessageConverter();

        HttpMessageConverters converters = new HttpMessageConverters(false, List.of(stringConverter));

        assertThat(converters.getConverters()).containsExactly(stringConverter);
        assertThat(converters).containsExactly(stringConverter);
    }

    @Test
    void additionalGsonConverterIsMergedWithDefaultConverters() {
        GsonHttpMessageConverter gsonConverter = new GsonHttpMessageConverter();

        HttpMessageConverters converters = new HttpMessageConverters(List.of(gsonConverter));

        assertThat(converters.getConverters()).contains(gsonConverter);
    }

}
