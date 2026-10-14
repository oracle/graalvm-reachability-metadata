/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.util.CustomObjectInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomObjectInputStreamTest {

    @Test
    void deserializesApplicationObjectWithProvidedClassLoader() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(new Payload("payload"));
        }

        try (CustomObjectInputStream input = new CustomObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()), getClass().getClassLoader())) {
            assertThat(input.readObject()).isEqualTo(new Payload("payload"));
        }
    }

    @Test
    void fallsBackToStandardResolutionForBootstrapClasses() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(new ArrayList<>(List.of("bootstrap-value")));
        }

        ClassLoader rejectingLoader = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                throw new ClassNotFoundException(name);
            }
        };
        try (CustomObjectInputStream input = new CustomObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()), rejectingLoader)) {
            assertThat(input.readObject()).isEqualTo(List.of("bootstrap-value"));
        }
    }

    public record Payload(String value) implements Serializable {
    }
}
