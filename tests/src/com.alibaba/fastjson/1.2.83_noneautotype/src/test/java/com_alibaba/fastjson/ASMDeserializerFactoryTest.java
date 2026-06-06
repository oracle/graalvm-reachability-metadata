/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.deserializer.JavaBeanDeserializer;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ASMDeserializerFactoryTest {
    @Test
    void parseObjectUsesGeneratedJavaBeanDeserializerWhenAsmIsEnabled() {
        ParserConfig config = new ParserConfig(ASMDeserializerFactoryTest.class.getClassLoader());
        config.setAsmEnable(true);

        try {
            AsmBackedBean bean = JSON.parseObject(
                    "{\"id\":7,\"name\":\"generated\",\"enabled\":true}", AsmBackedBean.class, config);
            ObjectDeserializer deserializer = config.getDeserializer(AsmBackedBean.class);

            assertThat(bean.id).isEqualTo(7);
            assertThat(bean.name).isEqualTo("generated");
            assertThat(bean.enabled).isTrue();
            assertThat(deserializer).isInstanceOf(JavaBeanDeserializer.class);
            assertThat(deserializer.getClass()).isNotEqualTo(JavaBeanDeserializer.class);
            assertThat(deserializer.getClass().getName()).contains("FastjsonASMDeserializer");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class AsmBackedBean {
        public int id;
        public String name;
        public boolean enabled;
    }
}
