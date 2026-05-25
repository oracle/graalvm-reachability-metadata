/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.ASMSerializerFactory;
import com.alibaba.fastjson.serializer.JavaBeanSerializer;
import com.alibaba.fastjson.serializer.SerializeBeanInfo;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.util.TypeUtils;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ASMSerializerFactoryTest {
    @Test
    void createJavaBeanSerializerDefinesAndInstantiatesGeneratedSerializer() throws Exception {
        try {
            SerializeBeanInfo beanInfo = TypeUtils.buildBeanInfo(AsmSerializedBean.class, null, null);
            ASMSerializerFactory factory = new ASMSerializerFactory();

            JavaBeanSerializer serializer = factory.createJavaBeanSerializer(beanInfo);

            SerializeConfig config = new SerializeConfig();
            config.put(AsmSerializedBean.class, serializer);
            String json = JSON.toJSONString(new AsmSerializedBean(7, "generated", true), config);

            assertThat(serializer.getClass()).isNotEqualTo(JavaBeanSerializer.class);
            assertThat(serializer.getClass().getName()).contains("ASMSerializer_");
            assertThat(json).contains("\"enabled\":true", "\"id\":7", "\"name\":\"generated\"");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class AsmSerializedBean {
        public int id;
        public String name;
        public boolean enabled;

        public AsmSerializedBean(int id, String name, boolean enabled) {
            this.id = id;
            this.name = name;
            this.enabled = enabled;
        }
    }
}
