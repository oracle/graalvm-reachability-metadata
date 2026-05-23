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
import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.awt.Point;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.OptionalInt;
import java.util.concurrent.atomic.LongAdder;
import oracle.sql.DATE;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import springfox.documentation.spring.web.json.Json;

public class SerializeConfigTest {
    @Test
    void getObjectWriterRegistersBuiltInAwtJdk8OracleSpringfoxAndJodaSerializers() {
        SerializeConfig config = new SerializeConfig();

        assertThat(config.getObjectWriter(Point.class)).isNotNull();
        assertThat(config.getObjectWriter(LocalDate.class)).isNotNull();
        assertThat(config.getObjectWriter(OptionalInt.class)).isNotNull();
        assertThat(config.getObjectWriter(LongAdder.class)).isNotNull();
        assertThat(config.getObjectWriter(DATE.class)).isNotNull();
        assertThat(config.getObjectWriter(Json.class)).isNotNull();
        assertThat(config.getObjectWriter(DateTime.class)).isNotNull();
    }

    @Test
    void jsonTypeCustomSerializerIsInstantiatedForJavaBeans() {
        CustomObjectSerializer.constructed = false;
        SerializeConfig config = new SerializeConfig();
        config.setAsmEnable(false);

        String json = JSON.toJSONString(new BeanWithCustomSerializer("fastjson"), config);

        assertThat(CustomObjectSerializer.constructed).isTrue();
        assertThat(json).isEqualTo("\"custom-fastjson\"");
    }

    @Test
    void enumJsonFieldMethodAndMixinMethodAreUsedAsSerializedValues() {
        SerializeConfig fieldConfig = new SerializeConfig();
        SerializeConfig mixInConfig = new SerializeConfig();

        String fieldValue = JSON.toJSONString(AnnotatedFieldEnum.ACTIVE, fieldConfig);
        JSON.addMixInAnnotations(MixInEnum.class, MixInEnumMixin.class);
        try {
            String mixInValue = JSON.toJSONString(MixInEnum.ACTIVE, mixInConfig);
            assertThat(mixInValue).isEqualTo("7");
        } finally {
            JSON.removeMixInAnnotations(MixInEnum.class);
        }

        assertThat(fieldValue).isEqualTo("5");
    }

    @JSONType(serializer = CustomObjectSerializer.class)
    public static class BeanWithCustomSerializer {
        private final String name;

        public BeanWithCustomSerializer(String name) {
            this.name = name;
        }
    }

    public static class CustomObjectSerializer implements ObjectSerializer {
        static boolean constructed;

        public CustomObjectSerializer() {
            constructed = true;
        }

        @Override
        public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features)
                throws IOException {
            BeanWithCustomSerializer bean = (BeanWithCustomSerializer) object;
            SerializeWriter writer = serializer.getWriter();
            writer.writeString("custom-" + bean.name);
        }
    }

    public enum AnnotatedFieldEnum {
        ACTIVE(5),
        INACTIVE(0);

        @JSONField
        public final int code;

        AnnotatedFieldEnum(int code) {
            this.code = code;
        }
    }

    public enum MixInEnum {
        ACTIVE(7),
        INACTIVE(0);

        private final int code;

        MixInEnum(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    public static class MixInEnumMixin {
        @JSONField(serialzeFeatures = SerializerFeature.WriteNonStringValueAsString)
        public int getCode() {
            return 0;
        }
    }
}
