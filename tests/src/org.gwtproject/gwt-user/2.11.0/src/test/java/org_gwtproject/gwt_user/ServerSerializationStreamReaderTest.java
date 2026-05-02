/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.XsrfToken;
import com.google.gwt.user.client.rpc.impl.AbstractSerializationStream;
import com.google.gwt.user.server.Base64Utils;
import com.google.gwt.user.server.rpc.SerializationPolicy;
import com.google.gwt.user.server.rpc.impl.DequeMap;
import com.google.gwt.user.server.rpc.impl.SerializabilityUtil;
import com.google.gwt.user.server.rpc.impl.ServerSerializationStreamReader;
import com.google.gwt.user.server.rpc.impl.TypeNameObfuscator;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class ServerSerializationStreamReaderTest {
    private static final String MODULE_BASE_URL = "module";
    private static final String STRONG_NAME = "policy";

    @Test
    void deserializesEnhancedClassServerFieldsAndClientFields() throws Exception {
        TestSerializationPolicy policy = new TestSerializationPolicy();
        policy.setClientFieldNames(DequeMap.class, Set.of("size"));
        List<String> stringTable = new ArrayList<>();
        int typeIndex = addTypeSignature(stringTable, DequeMap.class, policy);
        int serverFieldsIndex = addString(stringTable,
                encodeServerFields(Map.of("dequeCapacity", Integer.valueOf(9))));

        ServerSerializationStreamReader reader = newReader(policy,
                rpcPayload(AbstractSerializationStream.DEFAULT_FLAGS, stringTable,
                        String.valueOf(typeIndex), String.valueOf(serverFieldsIndex), "2"));

        DequeMap<String, String> result = cast(reader.deserializeValue(DequeMap.class));

        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    void invokesSetterForEnhancedClientField() throws Exception {
        TestSerializationPolicy policy = new TestSerializationPolicy();
        policy.setClientFieldNames(XsrfToken.class, Set.of("token"));
        List<String> stringTable = new ArrayList<>();
        int typeIndex = addTypeSignature(stringTable, XsrfToken.class, policy);
        int tokenIndex = addString(stringTable, "csrf-token");

        ServerSerializationStreamReader reader = newReader(policy,
                rpcPayload(AbstractSerializationStream.DEFAULT_FLAGS, stringTable,
                        String.valueOf(typeIndex), "0", String.valueOf(tokenIndex)));

        XsrfToken result = (XsrfToken) reader.deserializeValue(XsrfToken.class);

        assertThat(result.getToken()).isEqualTo("csrf-token");
    }

    @Test
    void resolvesElidedTypeNameThroughSerializationPolicy() throws Exception {
        TestSerializationPolicy policy = new TestSerializationPolicy();
        policy.setClientFieldNames(XsrfToken.class, Set.of("token"));
        policy.setTypeId("xsrf", XsrfToken.class);
        List<String> stringTable = new ArrayList<>();
        int typeIndex = addString(stringTable, "xsrf");
        int tokenIndex = addString(stringTable, "elided-token");

        ServerSerializationStreamReader reader = newReader(policy,
                rpcPayload(AbstractSerializationStream.FLAG_ELIDE_TYPE_NAMES, stringTable,
                        String.valueOf(typeIndex), "0", String.valueOf(tokenIndex)));

        XsrfToken result = (XsrfToken) reader.deserializeValue(XsrfToken.class);

        assertThat(result.getToken()).isEqualTo("elided-token");
    }

    @Test
    void usesStaticCustomSerializerMethodsForLogRecord() throws Exception {
        TestSerializationPolicy policy = new TestSerializationPolicy();
        List<String> stringTable = new ArrayList<>();
        int typeIndex = addTypeSignature(stringTable, LogRecord.class, policy);
        int levelIndex = addString(stringTable, Level.WARNING.getName());
        int messageIndex = addString(stringTable, "message from custom serializer");
        int loggerIndex = addString(stringTable, "rpc.logger");

        ServerSerializationStreamReader reader = newReader(policy,
                rpcPayload(AbstractSerializationStream.DEFAULT_FLAGS, stringTable,
                        String.valueOf(typeIndex), String.valueOf(levelIndex), String.valueOf(messageIndex),
                        String.valueOf(loggerIndex), Base64Utils.toBase64(42L), "0"));

        LogRecord result = (LogRecord) reader.deserializeValue(LogRecord.class);

        assertThat(result.getLevel()).isEqualTo(Level.WARNING);
        assertThat(result.getMessage()).isEqualTo("message from custom serializer");
        assertThat(result.getLoggerName()).isEqualTo("rpc.logger");
        assertThat(result.getMillis()).isEqualTo(42L);
        assertThat(result.getThrown()).isNull();
    }

    @Test
    void scansForCheckedStaticCustomSerializerMethodsWhenExpectedTypeIsProvided() throws Exception {
        TestSerializationPolicy policy = new TestSerializationPolicy();
        List<String> stringTable = new ArrayList<>();
        int typeIndex = addTypeSignature(stringTable, LogRecord.class, policy);
        int levelIndex = addString(stringTable, Level.INFO.getName());
        int messageIndex = addString(stringTable, "typed message");
        int loggerIndex = addString(stringTable, "typed.logger");

        ServerSerializationStreamReader reader = newReader(policy,
                rpcPayload(AbstractSerializationStream.DEFAULT_FLAGS, stringTable,
                        String.valueOf(typeIndex), String.valueOf(levelIndex), String.valueOf(messageIndex),
                        String.valueOf(loggerIndex), Base64Utils.toBase64(7L), "0"));

        LogRecord result = (LogRecord) reader.readObject(LogRecord.class, new DequeMap<TypeVariable<?>, Type>());

        assertThat(result.getLevel()).isEqualTo(Level.INFO);
        assertThat(result.getLoggerName()).isEqualTo("typed.logger");
        assertThat(result.getMillis()).isEqualTo(7L);
    }

    @Test
    void invokesCheckedStaticCustomSerializerMethodsWhenExpectedTypeIsProvided() throws Exception {
        TestSerializationPolicy policy = new TestSerializationPolicy();
        List<String> stringTable = new ArrayList<>();
        int typeIndex = addTypeSignature(stringTable, CheckedCustomValue.class, policy);
        int constructorValueIndex = addString(stringTable, "created");
        int deserializedValueIndex = addString(stringTable, "deserialized");

        ServerSerializationStreamReader reader = newReader(policy,
                rpcPayload(AbstractSerializationStream.DEFAULT_FLAGS, stringTable,
                        String.valueOf(typeIndex), String.valueOf(constructorValueIndex),
                        String.valueOf(deserializedValueIndex)));

        CheckedCustomValue result = (CheckedCustomValue) reader.readObject(
                CheckedCustomValue.class, new DequeMap<TypeVariable<?>, Type>());

        assertThat(result.getConstructorValue()).isEqualTo("created");
        assertThat(result.getDeserializedValue()).isEqualTo("deserialized");
    }

    private static ServerSerializationStreamReader newReader(SerializationPolicy policy,
            String payload) throws SerializationException {
        ServerSerializationStreamReader reader = new ServerSerializationStreamReader(
                ServerSerializationStreamReaderTest.class.getClassLoader(),
                (moduleBaseURL, strongName) -> policy);
        reader.prepareToRead(payload);
        return reader;
    }

    private static String rpcPayload(int flags, List<String> stringTable, String... payloadTokens) {
        StringBuilder payload = new StringBuilder();
        append(payload, String.valueOf(AbstractSerializationStream.SERIALIZATION_STREAM_VERSION));
        append(payload, String.valueOf(flags));
        append(payload, String.valueOf(stringTable.size()));
        for (String tableEntry : stringTable) {
            append(payload, tableEntry);
        }
        append(payload, String.valueOf(stringTable.indexOf(MODULE_BASE_URL) + 1));
        append(payload, String.valueOf(stringTable.indexOf(STRONG_NAME) + 1));
        for (String payloadToken : payloadTokens) {
            append(payload, payloadToken);
        }
        return payload.toString();
    }

    private static void append(StringBuilder payload, String token) {
        payload.append(token).append(AbstractSerializationStream.RPC_SEPARATOR_CHAR);
    }

    private static int addTypeSignature(List<String> stringTable, Class<?> type,
            SerializationPolicy policy) {
        return addString(stringTable, SerializabilityUtil.encodeSerializedInstanceReference(type, policy));
    }

    private static int addString(List<String> stringTable, String value) {
        if (!stringTable.contains(MODULE_BASE_URL)) {
            stringTable.add(MODULE_BASE_URL);
            stringTable.add(STRONG_NAME);
        }
        stringTable.add(value);
        return stringTable.size();
    }

    private static String encodeServerFields(Map<String, Object> serverFields) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes);
        output.writeInt(serverFields.size());
        for (Map.Entry<String, Object> entry : serverFields.entrySet()) {
            output.writeObject(entry.getKey());
            output.writeObject(entry.getValue());
        }
        output.close();
        return Base64Utils.toBase64(bytes.toByteArray());
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    public static final class CheckedCustomValue {
        private final String constructorValue;
        private String deserializedValue;

        public CheckedCustomValue(String constructorValue) {
            this.constructorValue = constructorValue;
        }

        public String getConstructorValue() {
            return constructorValue;
        }

        public String getDeserializedValue() {
            return deserializedValue;
        }
    }

    public static final class CheckedCustomValue_CustomFieldSerializer {
        public static CheckedCustomValue instantiateChecked(ServerSerializationStreamReader reader,
                Type[] expectedParameterTypes, DequeMap<TypeVariable<?>, Type> resolvedTypes)
                throws SerializationException {
            return new CheckedCustomValue(reader.readString());
        }

        public static void deserializeChecked(ServerSerializationStreamReader reader,
                CheckedCustomValue instance, Type[] expectedParameterTypes,
                DequeMap<TypeVariable<?>, Type> resolvedTypes) throws SerializationException {
            instance.deserializedValue = reader.readString();
        }
    }

    private static final class TestSerializationPolicy extends SerializationPolicy
            implements TypeNameObfuscator {
        private final Map<Class<?>, Set<String>> clientFields = new LinkedHashMap<>();
        private final Map<String, String> classesByTypeId = new LinkedHashMap<>();
        private final Map<Class<?>, String> typeIdsByClass = new LinkedHashMap<>();

        void setClientFieldNames(Class<?> type, Set<String> fieldNames) {
            clientFields.put(type, fieldNames);
        }

        void setTypeId(String typeId, Class<?> type) {
            classesByTypeId.put(typeId, type.getName());
            typeIdsByClass.put(type, typeId);
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

        @Override
        public String getClassNameForTypeId(String id) throws SerializationException {
            String className = classesByTypeId.get(id);
            if (className == null) {
                throw new SerializationException("Unknown type id: " + id);
            }
            return className;
        }

        @Override
        public String getTypeIdForClass(Class<?> clazz) throws SerializationException {
            String typeId = typeIdsByClass.get(clazz);
            if (typeId == null) {
                throw new SerializationException("Unknown class: " + clazz.getName());
            }
            return typeId;
        }
    }
}
