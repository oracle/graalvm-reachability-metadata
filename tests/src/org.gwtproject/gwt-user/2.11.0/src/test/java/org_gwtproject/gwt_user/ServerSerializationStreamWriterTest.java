/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.server.rpc.SerializationPolicy;
import com.google.gwt.user.server.rpc.impl.ServerSerializationStreamWriter;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ServerSerializationStreamWriterTest {
    @Test
    void serializesEnhancedClassServerFieldsAndClientFields() throws Exception {
        TestSerializationPolicy policy = new TestSerializationPolicy();
        policy.setClientFieldNames(EnhancedRpcValue.class, Set.of("clientVisible"));
        ServerSerializationStreamWriter writer = newWriter(policy);

        writer.writeObject(new EnhancedRpcValue("client-visible-value", "server-only-value", 42));

        String payload = writer.toString();

        assertThat(payload).contains(EnhancedRpcValue.class.getName());
        assertThat(payload).contains("client-visible-value");
        assertThat(payload).doesNotContain("server-only-value");
    }

    @Test
    void invokesStaticCustomSerializerDiscoveredForValueType() throws Exception {
        ServerSerializationStreamWriter writer = newWriter(new TestSerializationPolicy());

        writer.writeObject(new CustomSerializedValue("custom-field", 7));

        String payload = writer.toString();

        assertThat(payload).contains(CustomSerializedValue.class.getName());
        assertThat(payload).contains("serialized-custom-field");
        assertThat(payload).contains("107");
    }

    private static ServerSerializationStreamWriter newWriter(SerializationPolicy policy) {
        ServerSerializationStreamWriter writer = new ServerSerializationStreamWriter(policy);
        writer.prepareToWrite();
        return writer;
    }

    public static final class EnhancedRpcValue {
        private String clientVisible;
        private String serverNote;
        private int serverNumber;

        public EnhancedRpcValue(String clientVisible, String serverNote, int serverNumber) {
            this.clientVisible = clientVisible;
            this.serverNote = serverNote;
            this.serverNumber = serverNumber;
        }
    }

    public static final class CustomSerializedValue {
        private final String label;
        private final int number;

        public CustomSerializedValue(String label, int number) {
            this.label = label;
            this.number = number;
        }
    }

    public static final class CustomSerializedValue_CustomFieldSerializer {
        public static void serialize(ServerSerializationStreamWriter writer,
                CustomSerializedValue value) throws SerializationException {
            writer.writeString("serialized-" + value.label);
            writer.writeInt(value.number + 100);
        }
    }

    private static final class TestSerializationPolicy extends SerializationPolicy {
        private final Map<Class<?>, Set<String>> clientFields = new LinkedHashMap<>();

        void setClientFieldNames(Class<?> type, Set<String> fieldNames) {
            clientFields.put(type, fieldNames);
        }

        @Override
        public Set<String> getClientFieldNamesForEnhancedClass(Class<?> clazz) {
            return clientFields.get(clazz);
        }

        @Override
        public boolean shouldDeserializeFields(Class<?> clazz) {
            return clazz != null && clazz != Object.class;
        }

        @Override
        public boolean shouldSerializeFields(Class<?> clazz) {
            return clazz != null && clazz != Object.class;
        }

        @Override
        public void validateDeserialize(Class<?> clazz) {
        }

        @Override
        public void validateSerialize(Class<?> clazz) {
        }
    }
}
