/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.user.client.rpc.core.java.util.LinkedHashMap_CustomFieldSerializer;
import com.google.gwt.user.server.rpc.SerializationPolicy;
import com.google.gwt.user.server.rpc.impl.SerializabilityUtil;
import com.google.gwt.user.server.rpc.impl.ServerSerializationStreamWriter;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

public class SerializabilityUtilTest {
    @Test
    void instantiatesClassBasedCustomFieldSerializerThroughRpcWriter() throws Exception {
        LinkedHashMap<String, String> value = new LinkedHashMap<>();
        value.put("first", "alpha");
        value.put("second", "beta");
        ServerSerializationStreamWriter writer = new ServerSerializationStreamWriter(new AllowAllSerializationPolicy());
        writer.prepareToWrite();

        writer.writeObject(value);

        assertThat(SerializabilityUtil.hasCustomFieldSerializer(LinkedHashMap.class))
                .isEqualTo(LinkedHashMap_CustomFieldSerializer.class);
        assertThat(writer.toString())
                .contains(LinkedHashMap.class.getName())
                .contains("first", "alpha", "second", "beta");
    }

    private static final class AllowAllSerializationPolicy extends SerializationPolicy {
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
