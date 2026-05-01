/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe.config;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigOriginFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigExceptionTest {
    @Test
    void deserializesExceptionWithOrigin() throws Exception {
        ConfigOrigin origin = ConfigOriginFactory.newFile("application.conf").withLineNumber(12);
        ConfigException.Parse original = new ConfigException.Parse(origin, "invalid value");

        ConfigException.Parse restored = deserialize(serialize(original));

        assertThat(restored).isInstanceOf(ConfigException.Parse.class);
        assertThat(restored.getMessage()).isEqualTo(original.getMessage());
        assertThat(restored.origin()).isNotNull();
        assertThat(restored.origin().description()).isEqualTo(origin.description());
        assertThat(restored.origin().filename()).isEqualTo(origin.filename());
        assertThat(restored.origin().lineNumber()).isEqualTo(origin.lineNumber());
    }

    private static byte[] serialize(ConfigException exception) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(exception);
        }

        return outputStream.toByteArray();
    }

    private static ConfigException.Parse deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (ConfigException.Parse) objectInputStream.readObject();
        }
    }
}
