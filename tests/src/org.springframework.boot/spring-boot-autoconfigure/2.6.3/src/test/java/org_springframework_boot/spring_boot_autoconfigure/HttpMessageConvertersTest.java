/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpMessageConvertersTest {

    @Test
    void createsContainerWithProvidedConverters() {
        HttpMessageConverter<String> converter = new StringHttpMessageConverter();
        List<HttpMessageConverter<?>> additionalConverters = List.of(converter);

        HttpMessageConverters converters = new HttpMessageConverters(false, additionalConverters);

        assertThat(converters.getConverters()).containsExactly(converter);
        assertThat(converters).containsExactly(converter);
    }

}
