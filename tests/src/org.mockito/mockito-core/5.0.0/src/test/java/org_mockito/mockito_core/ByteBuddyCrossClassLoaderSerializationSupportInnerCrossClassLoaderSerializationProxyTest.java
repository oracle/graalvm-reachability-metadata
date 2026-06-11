/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;
import org.mockito.mock.SerializableMode;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteBuddyCrossClassLoaderSerializationSupportInnerCrossClassLoaderSerializationProxyTest {
    @Test
    void crossClassLoaderSerializableMockCanBeWrittenToObjectStream() throws Exception {
        try {
            SerializableGreeting greeting =
                    Mockito.mock(
                            SerializableGreeting.class,
                            Mockito.withSettings()
                                    .mockMaker(MockMakers.SUBCLASS)
                                    .serializable(SerializableMode.ACROSS_CLASSLOADERS));
            Mockito.when(greeting.greet("Mockito")).thenReturn("Hello Mockito");

            byte[] serialized = serialize(greeting);

            assertThat(serialized).isNotEmpty();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }


    public interface SerializableGreeting extends Serializable {
        String greet(String name);
    }
}
