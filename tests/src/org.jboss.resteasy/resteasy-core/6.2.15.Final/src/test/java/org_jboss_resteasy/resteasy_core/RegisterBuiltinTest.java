/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.ext.MessageBodyWriter;

import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.plugins.providers.StringTextStar;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class RegisterBuiltinTest {
    @Test
    void discoversAndRegistersUsableBuiltinProviders() throws Exception {
        assertThat(RegisterBuiltin.scanBuiltins()).containsKey(StringTextStar.class.getName());
        assertThat(RegisterBuiltin.scanBuiltins(Set.of(StringTextStar.class.getName()))).doesNotContainKey(StringTextStar.class.getName());
        ResteasyProviderFactoryImpl factory = new ResteasyProviderFactoryImpl();
        RegisterBuiltin.register(factory);
        Annotation[] annotations = new Annotation[0];
        MessageBodyWriter<String> writer = factory.getMessageBodyWriter(String.class, String.class, annotations, MediaType.TEXT_PLAIN_TYPE);
        assertThat(writer).isNotNull();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.writeTo("builtin-text", String.class, String.class, annotations, MediaType.TEXT_PLAIN_TYPE, new MultivaluedHashMap<>(), output);
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("builtin-text");
        assertThat(factory.isBuiltinsRegistered()).isTrue();
    }
}
