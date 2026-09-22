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

import org.apache.catalina.util.CustomObjectInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomObjectInputStreamTest {

    @Test
    void restoresSerializedValueWithConfiguredClassLoader() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(Integer.valueOf(29));
        }

        try (CustomObjectInputStream input = new CustomObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()), Integer.class.getClassLoader())) {
            assertThat(input.readObject()).isEqualTo(29);
        }
    }
}
