/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.Serializable;
import java.util.Locale;

import com.thoughtworks.xstream.mapper.DefaultMapper;
import com.thoughtworks.xstream.mapper.LambdaMapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LambdaMapperTest {
    @Test
    void serializesSerializableLambdaWithAdditionalInterfaceAsFunctionalInterface() {
        SerializableNormalizer normalizer = (SerializableNormalizer & NamedCapability)value ->
            value.toUpperCase(Locale.ROOT);
        LambdaMapper mapper = new LambdaMapper(
            new DefaultMapper(LambdaMapperTest.class.getClassLoader()));

        String serializedClass = mapper.serializedClass(normalizer.getClass());

        assertThat(serializedClass)
            .isIn(SerializableNormalizer.class.getName(), normalizer.getClass().getName());
        assertThat(normalizer.normalize("lambda mapper")).isEqualTo("LAMBDA MAPPER");
        assertThat(((NamedCapability)normalizer).capabilityName()).isEqualTo("normalizer");
    }

    @FunctionalInterface
    public interface SerializableNormalizer extends Serializable {
        String normalize(String value);
    }

    public interface NamedCapability {
        default String capabilityName() {
            return "normalizer";
        }
    }
}
