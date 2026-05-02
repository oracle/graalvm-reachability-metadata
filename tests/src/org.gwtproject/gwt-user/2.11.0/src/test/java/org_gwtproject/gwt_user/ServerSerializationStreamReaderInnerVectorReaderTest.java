/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.impl.AbstractSerializationStream;
import com.google.gwt.user.server.rpc.SerializationPolicy;
import com.google.gwt.user.server.rpc.impl.SerializabilityUtil;
import com.google.gwt.user.server.rpc.impl.ServerSerializationStreamReader;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ServerSerializationStreamReaderInnerVectorReaderTest {
    private static final String MODULE_BASE_URL = "module";
    private static final String STRONG_NAME = "policy";

    @Test
    void deserializesPrimitiveArrayThroughVectorReader() throws Exception {
        TestSerializationPolicy policy = new TestSerializationPolicy();
        List<String> stringTable = new ArrayList<>();
        int typeIndex = addTypeSignature(stringTable, int[].class, policy);
        ServerSerializationStreamReader reader = newReader(policy,
                rpcPayload(AbstractSerializationStream.DEFAULT_FLAGS, stringTable,
                        String.valueOf(typeIndex), "3", "2", "3", "5"));

        int[] result = (int[]) reader.deserializeValue(int[].class);

        assertThat(result).containsExactly(2, 3, 5);
    }

    private static ServerSerializationStreamReader newReader(SerializationPolicy policy,
            String payload) throws SerializationException {
        ServerSerializationStreamReader reader = new ServerSerializationStreamReader(
                ServerSerializationStreamReaderInnerVectorReaderTest.class.getClassLoader(),
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
        String typeSignature = SerializabilityUtil.encodeSerializedInstanceReference(type, policy);
        return addString(stringTable, typeSignature);
    }

    private static int addString(List<String> stringTable, String value) {
        if (!stringTable.contains(MODULE_BASE_URL)) {
            stringTable.add(MODULE_BASE_URL);
            stringTable.add(STRONG_NAME);
        }
        stringTable.add(value);
        return stringTable.size();
    }

    private static final class TestSerializationPolicy extends SerializationPolicy {
        @Override
        public Set<String> getClientFieldNamesForEnhancedClass(Class<?> clazz) {
            return null;
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
