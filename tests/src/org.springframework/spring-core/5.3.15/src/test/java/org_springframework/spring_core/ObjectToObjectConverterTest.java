/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.MimeType;

public class ObjectToObjectConverterTest {

    private final ConversionService conversionService = new DefaultConversionService();

    @Test
    void invokesSourceInstanceToTargetMethodConvention() {
        ResolvableType resolvableType = ResolvableType.forClass(String.class);

        Class<?> convertedClass = conversionService.convert(resolvableType, Class.class);

        assertThat(convertedClass).isSameAs(String.class);
    }

    @Test
    void invokesStaticValueOfFactoryMethodConvention() {
        MimeType mimeType = conversionService.convert("text/plain;charset=UTF-8", MimeType.class);

        assertThat(mimeType).isNotNull();
        assertThat(mimeType.getType()).isEqualTo("text");
        assertThat(mimeType.getSubtype()).isEqualTo("plain");
        assertThat(mimeType.getCharset()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void invokesSingleArgumentConstructorConvention() {
        NamedThreadLocal<?> threadLocal = conversionService.convert(
                "converter-thread-local",
                NamedThreadLocal.class
        );

        assertThat(threadLocal).isNotNull();
        assertThat(threadLocal.toString()).isEqualTo("converter-thread-local");
    }
}
