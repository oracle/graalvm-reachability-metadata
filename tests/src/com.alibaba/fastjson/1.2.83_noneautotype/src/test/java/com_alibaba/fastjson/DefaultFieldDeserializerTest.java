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
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;

import java.lang.reflect.Type;
import org.junit.jupiter.api.Test;

public class DefaultFieldDeserializerTest {
    @Test
    void parseObjectInstantiatesJsonFieldCustomDeserializer() {
        PrefixingDeserializer.constructed = false;

        BeanWithCustomFieldDeserializer bean = JSON.parseObject("{\"value\":\"fastjson\"}",
                BeanWithCustomFieldDeserializer.class, noAsmConfig());

        assertThat(PrefixingDeserializer.constructed).isTrue();
        assertThat(bean.value).isEqualTo("custom:fastjson");
    }

    private static ParserConfig noAsmConfig() {
        ParserConfig config = new ParserConfig();
        config.setAsmEnable(false);
        return config;
    }

    public static class BeanWithCustomFieldDeserializer {
        @JSONField(deserializeUsing = PrefixingDeserializer.class)
        public String value;
    }

    public static class PrefixingDeserializer implements ObjectDeserializer {
        static boolean constructed;

        public PrefixingDeserializer() {
            constructed = true;
        }

        @Override
        public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
            String value = parser.parseObject(String.class);
            return (T) ("custom:" + value);
        }

        @Override
        public int getFastMatchToken() {
            return JSONToken.LITERAL_STRING;
        }
    }
}
