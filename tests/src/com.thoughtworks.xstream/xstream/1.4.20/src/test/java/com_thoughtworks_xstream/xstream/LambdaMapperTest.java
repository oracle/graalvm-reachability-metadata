/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Base64;
import java.util.Locale;

import com.thoughtworks.xstream.mapper.DefaultMapper;
import com.thoughtworks.xstream.mapper.LambdaMapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LambdaMapperTest {
    // Synthetic hidden class named like legacy JVM lambda proxies matched by LambdaMapper.
    private static final byte[] LEGACY_NAMED_LAMBDA_PROXY_BYTES = Base64.getMimeDecoder().decode("""
            yv66vgAAADQAEgEAO2NvbV90aG91Z2h0d29ya3NfeHN0cmVhbS94c3RyZWFtL0xhbWJkYU1hcHBlclRl
            c3QkJExhbWJkYSQxBwABAQAQamF2YS9sYW5nL09iamVjdAcAAwEASGNvbV90aG91Z2h0d29ya3NfeHN0
            cmVhbS94c3RyZWFtL0xhbWJkYU1hcHBlclRlc3QkU2VyaWFsaXphYmxlTm9ybWFsaXplcgcABQEAQWNv
            bV90aG91Z2h0d29ya3NfeHN0cmVhbS94c3RyZWFtL0xhbWJkYU1hcHBlclRlc3QkTmFtZWRDYXBhYmls
            aXR5BwAHAQAGPGluaXQ+AQADKClWDAAJAAoKAAQACwEABENvZGUBAAlub3JtYWxpemUBACYoTGphdmEv
            bGFuZy9TdHJpbmc7KUxqYXZhL2xhbmcvU3RyaW5nOwEAClNvdXJjZUZpbGUBABVMYW1iZGFNYXBwZXJU
            ZXN0LmphdmEQMQACAAQAAgAGAAgAAAACAAEACQAKAAEADQAAABEAAQABAAAABSq3AAyxAAAAAAABAA4A
            DwABAA0AAAAOAAEAAgAAAAIrsAAAAAAAAQAQAAAAAgAR
            """);

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

    @Test
    void serializesLegacyNamedLambdaProxyWithAdditionalInterfaceAsFunctionalInterface()
        throws IllegalAccessException {
        Class<?> lambdaType = legacyNamedLambdaProxyType();
        LambdaMapper mapper = new LambdaMapper(
            new DefaultMapper(LambdaMapperTest.class.getClassLoader()));

        String serializedClass = mapper.serializedClass(lambdaType);

        if (lambdaType.getSimpleName().matches(".*\\$\\$Lambda\\$[0-9]+/.*")) {
            assertThat(serializedClass).isEqualTo(SerializableNormalizer.class.getName());
        } else {
            assertThat(serializedClass).isEqualTo(lambdaType.getName());
        }
    }

    private static Class<?> legacyNamedLambdaProxyType() throws IllegalAccessException {
        try {
            return MethodHandles.lookup()
                .defineHiddenClass(LEGACY_NAMED_LAMBDA_PROXY_BYTES, false)
                .lookupClass();
        } catch (UnsupportedOperationException | LinkageError ex) {
            SerializableNormalizer normalizer = (SerializableNormalizer & NamedCapability)value -> value;
            return normalizer.getClass();
        }
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
