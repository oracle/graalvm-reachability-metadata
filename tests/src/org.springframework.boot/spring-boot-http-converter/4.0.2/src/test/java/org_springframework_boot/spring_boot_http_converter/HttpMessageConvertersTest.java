/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_http_converter;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConverters;
import org.springframework.hateoas.server.mvc.TypeConstrainedJacksonJsonHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SuppressWarnings("deprecation")
public class HttpMessageConvertersTest {

    @Test
    void createsImmutableIterableConverterCollectionWithoutDefaultConverters() {
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        TypeConstrainedJacksonJsonHttpMessageConverter constrainedJsonConverter =
                new TypeConstrainedJacksonJsonHttpMessageConverter(String.class);
        List<HttpMessageConverter<?>> additionalConverters = List.of(stringConverter, constrainedJsonConverter);

        HttpMessageConverters converters = new HttpMessageConverters(false, additionalConverters);

        assertThat(converters.getConverters()).containsExactly(stringConverter, constrainedJsonConverter);
        assertThat(converters).containsExactly(stringConverter, constrainedJsonConverter);
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> converters.getConverters().add(stringConverter));
    }

}
