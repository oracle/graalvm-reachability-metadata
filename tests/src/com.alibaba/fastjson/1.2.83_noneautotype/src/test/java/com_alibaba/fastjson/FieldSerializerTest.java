/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import java.io.IOException;
import java.lang.reflect.Type;
import org.junit.jupiter.api.Test;

public class FieldSerializerTest {
    @Test
    void jsonFieldSerializeUsingInstantiatesCustomFieldSerializer() {
        PrefixingStringSerializer.constructed = false;

        String json = JSON.toJSONString(new BeanWithCustomFieldSerializer("field-value"));

        assertThat(PrefixingStringSerializer.constructed).isTrue();
        assertThat(json).isEqualTo("{\"value\":\"custom-field-value\"}");
    }

    public static class BeanWithCustomFieldSerializer {
        @JSONField(serializeUsing = PrefixingStringSerializer.class)
        public final String value;

        public BeanWithCustomFieldSerializer(String value) {
            this.value = value;
        }
    }

    public static class PrefixingStringSerializer implements ObjectSerializer {
        static boolean constructed;

        public PrefixingStringSerializer() {
            constructed = true;
        }

        @Override
        public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features)
                throws IOException {
            serializer.write("custom-" + object);
        }
    }
}
