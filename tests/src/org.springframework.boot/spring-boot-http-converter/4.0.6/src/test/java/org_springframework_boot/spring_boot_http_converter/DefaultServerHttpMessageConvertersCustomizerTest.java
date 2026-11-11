/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_http_converter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConverters;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters.ServerBuilder;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.converter.HttpMessageConverters.forServer;

@SuppressWarnings("deprecation")
public class DefaultServerHttpMessageConvertersCustomizerTest {
    @Test
    void customizerRegistersDefaultsAndUserConverter() {
        StringHttpMessageConverter custom = new StringHttpMessageConverter(StandardCharsets.UTF_16BE);
        try (GenericApplicationContext context = ConverterCustomizerContext.create(
                "serverConvertersCustomizer", ServerHttpMessageConvertersCustomizer.class, null, custom)) {
            ServerHttpMessageConvertersCustomizer customizer = context.getBean(
                    "serverConvertersCustomizer", ServerHttpMessageConvertersCustomizer.class);
            ServerBuilder builder = forServer();
            customizer.customize(builder);
            List<HttpMessageConverter<?>> converters = new ArrayList<>();
            builder.build().forEach(converters::add);

            assertTrue(converters.contains(custom));
            assertTrue(converters.stream()
                    .anyMatch(converter -> converter instanceof ByteArrayHttpMessageConverter));
            assertEquals(StandardCharsets.UTF_16BE, custom.getDefaultCharset());
        }
    }

    @Test
    void customizerUsesLegacyConverterSet() {
        StringHttpMessageConverter custom = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        HttpMessageConverters legacy = new HttpMessageConverters(false, List.of(custom));
        try (GenericApplicationContext context = ConverterCustomizerContext.create(
                "serverConvertersCustomizer", ServerHttpMessageConvertersCustomizer.class, legacy)) {
            ServerHttpMessageConvertersCustomizer customizer = context.getBean(
                    "serverConvertersCustomizer", ServerHttpMessageConvertersCustomizer.class);
            ServerBuilder builder = forServer();
            customizer.customize(builder);
            List<HttpMessageConverter<?>> converters = new ArrayList<>();
            builder.build().forEach(converters::add);

            assertEquals(2, converters.size());
            assertSame(custom, converters.getFirst());
            assertInstanceOf(AllEncompassingFormHttpMessageConverter.class, converters.getLast());
        }
    }
}
