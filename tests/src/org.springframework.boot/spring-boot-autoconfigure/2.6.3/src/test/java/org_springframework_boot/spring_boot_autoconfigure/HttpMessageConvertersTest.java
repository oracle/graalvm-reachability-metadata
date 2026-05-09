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
import org.springframework.hateoas.server.mvc.TypeConstrainedMappingJackson2HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

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

    @Test
    void keepsTypeConstrainedJacksonConverterAlongsideDefaultJacksonConverter() {
        TypeConstrainedMappingJackson2HttpMessageConverter constrainedConverter =
                new TypeConstrainedMappingJackson2HttpMessageConverter(Object.class);
        List<HttpMessageConverter<?>> additionalConverters = List.of(constrainedConverter);

        HttpMessageConverters converters = new HttpMessageConverters(additionalConverters);

        assertThat(converters.getConverters()).contains(constrainedConverter);
        assertThat(converters.getConverters()).anySatisfy((converter) -> {
            assertThat(converter).isInstanceOf(MappingJackson2HttpMessageConverter.class);
            assertThat(converter).isNotSameAs(constrainedConverter);
        });
    }

}
