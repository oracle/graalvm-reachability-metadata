/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONCreator;
import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.google.common.collect.HashMultimap;

import java.awt.Point;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.OptionalInt;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

public class ParserConfigTest {
    @Test
    void getDeserializerRegistersBuiltInOptionalAwtJodaAndGuavaCodecs() {
        ParserConfig config = new ParserConfig();

        ObjectDeserializer pointDeserializer = config.getDeserializer(Point.class);
        ObjectDeserializer localDateDeserializer = config.getDeserializer(LocalDate.class);
        ObjectDeserializer optionalIntDeserializer = config.getDeserializer(OptionalInt.class);
        ObjectDeserializer dateTimeDeserializer = config.getDeserializer(DateTime.class);
        ObjectDeserializer multimapDeserializer = config.getDeserializer(HashMultimap.class);

        assertThat(pointDeserializer).isNotNull();
        assertThat(localDateDeserializer).isNotNull();
        assertThat(optionalIntDeserializer).isNotNull();
        assertThat(dateTimeDeserializer).isNotNull();
        assertThat(multimapDeserializer).isNotNull();
    }

    @Test
    void getDeserializerSupportsEnumJsonTypeDeserializerCreatorAndMixinCreator() {
        ParserConfig enumDeserializerConfig = new ParserConfig();
        CustomEnumDeserializer.constructed = false;

        CustomEnum customEnum = JSON.parseObject("1", CustomEnum.class, enumDeserializerConfig);

        ParserConfig creatorConfig = new ParserConfig();
        creatorConfig.setJacksonCompatible(true);
        CreatorEnum creatorEnum = JSON.parseObject("\"created\"", CreatorEnum.class, creatorConfig);

        ParserConfig mixInConfig = new ParserConfig();
        JSON.addMixInAnnotations(MixInCreatorEnum.class, MixInCreatorEnumMixin.class);
        try {
            MixInCreatorEnum mixInCreatorEnum = JSON.parseObject("\"mixed\"", MixInCreatorEnum.class, mixInConfig);
            assertThat(mixInCreatorEnum).isEqualTo(MixInCreatorEnum.MIXED);
        } finally {
            JSON.removeMixInAnnotations(MixInCreatorEnum.class);
        }

        assertThat(CustomEnumDeserializer.constructed).isTrue();
        assertThat(customEnum).isEqualTo(CustomEnum.SECOND);
        assertThat(creatorEnum).isEqualTo(CreatorEnum.CREATED);
    }

    @Test
    void createJavaBeanDeserializerUsesJsonTypeCustomDeserializer() {
        ParserConfig config = new ParserConfig();
        config.setAsmEnable(true);
        CustomBeanDeserializer.constructed = false;

        ObjectDeserializer deserializer = config.createJavaBeanDeserializer(CustomBean.class, CustomBean.class);
        CustomBean bean = parseWithDeserializer("{}", CustomBean.class, deserializer, config);

        assertThat(CustomBeanDeserializer.constructed).isTrue();
        assertThat(bean.source).isEqualTo("custom");
    }

    @Test
    void checkAutoTypeUsesRegisteredHandlers() {
        ParserConfig config = new ParserConfig();
        RecordingAutoTypeCheckHandler handler = new RecordingAutoTypeCheckHandler(DefaultLoaderAutoTypeBean.class);
        config.addAutoTypeCheckHandler(handler);

        Class<?> resolvedClass = config.checkAutoType(
                DefaultLoaderAutoTypeBean.class.getName(), ConfiguredLoaderAutoTypeBean.class, 123);

        assertThat(resolvedClass).isEqualTo(DefaultLoaderAutoTypeBean.class);
        assertThat(handler.typeName).isEqualTo(DefaultLoaderAutoTypeBean.class.getName());
        assertThat(handler.expectClass).isEqualTo(ConfiguredLoaderAutoTypeBean.class);
        assertThat(handler.features).isEqualTo(123);
    }

    private static <T> T parseWithDeserializer(
            String json, Class<T> beanClass, ObjectDeserializer deserializer, ParserConfig config) {
        DefaultJSONParser parser = new DefaultJSONParser(json, config);
        try {
            return deserializer.deserialze(parser, beanClass, null);
        } finally {
            parser.close();
        }
    }

    public enum CreatorEnum {
        CREATED,
        OTHER;

        @JSONCreator
        public static CreatorEnum fromJson(String value) {
            return "created".equals(value) ? CREATED : OTHER;
        }
    }

    public enum MixInCreatorEnum {
        MIXED,
        OTHER;

        public static MixInCreatorEnum fromJson(String value) {
            return "mixed".equals(value) ? MIXED : OTHER;
        }
    }

    public static class MixInCreatorEnumMixin {
        @JSONCreator
        public static MixInCreatorEnum fromJson(String value) {
            return null;
        }
    }

    @JSONType(deserializer = CustomEnumDeserializer.class)
    public enum CustomEnum {
        FIRST,
        SECOND
    }

    public static class CustomEnumDeserializer implements ObjectDeserializer {
        static boolean constructed;

        public CustomEnumDeserializer() {
            constructed = true;
        }

        @Override
        public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
            Integer value = parser.parseObject(Integer.class);
            return (T) (Integer.valueOf(1).equals(value) ? CustomEnum.SECOND : CustomEnum.FIRST);
        }

        @Override
        public int getFastMatchToken() {
            return 0;
        }
    }

    @JSONType(deserializer = CustomBeanDeserializer.class)
    public static class CustomBean {
        public final String source;

        public CustomBean() {
            this("default");
        }

        CustomBean(String source) {
            this.source = source;
        }
    }

    public static class CustomBeanDeserializer implements ObjectDeserializer {
        static boolean constructed;

        public CustomBeanDeserializer() {
            constructed = true;
        }

        @Override
        public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
            parser.parseObject();
            return (T) new CustomBean("custom");
        }

        @Override
        public int getFastMatchToken() {
            return 0;
        }
    }

    public static class RecordingAutoTypeCheckHandler implements ParserConfig.AutoTypeCheckHandler {
        private final Class<?> supportedClass;
        String typeName;
        Class<?> expectClass;
        int features;

        RecordingAutoTypeCheckHandler(Class<?> supportedClass) {
            this.supportedClass = supportedClass;
        }

        @Override
        public Class<?> handler(String typeName, Class<?> expectClass, int features) {
            this.typeName = typeName;
            this.expectClass = expectClass;
            this.features = features;
            return supportedClass.getName().equals(typeName) ? supportedClass : null;
        }
    }

    @JSONType
    public static class DefaultLoaderAutoTypeBean {
    }

    @JSONType
    public static class ConfiguredLoaderAutoTypeBean {
    }
}
