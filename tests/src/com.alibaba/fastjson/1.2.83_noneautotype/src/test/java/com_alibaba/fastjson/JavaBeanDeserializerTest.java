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
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.annotation.JSONPOJOBuilder;
import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.deserializer.JavaBeanDeserializer;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.util.FieldInfo;
import com.alibaba.fastjson.util.JavaBeanInfo;
import com.alibaba.fastjson.util.TypeUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaBeanDeserializerTest {
    @Test
    void parseObjectCreatesDefaultFactoryProxyInnerAndAutoTypeHandlerInstances() {
        ParserConfig config = noAsmConfig();
        RecordingAutoTypeCheckHandler.constructed = false;

        AutoTypeCheckedBean autoTypeChecked = JSON.parseObject(
                "{\"name\":\"handler\"}", AutoTypeCheckedBean.class, config);
        FactoryDefaultBean factoryDefault = JSON.parseObject("{}", FactoryDefaultBean.class, config);
        ProxyView proxyView = JSON.parseObject("{}", ProxyView.class, config);
        OuterBean outerBean = JSON.parseObject("{\"inner\":{\"value\":7}}", OuterBean.class, config);

        assertThat(autoTypeChecked.name).isEqualTo("handler");
        assertThat(RecordingAutoTypeCheckHandler.constructed).isTrue();
        assertThat(factoryDefault.source).isEqualTo("factory");
        assertThat(proxyView).isNotNull();
        assertThat(outerBean.inner.value).isEqualTo(7);
    }

    @Test
    void parseObjectUsesCreatorConstructorFactoryMethodBuilderAndSingleValueFactory() {
        ParserConfig config = noAsmConfig();

        CreatorConstructorBean creatorConstructor = JSON.parseObject(
                "{\"name\":\"constructor\",\"number\":3}", CreatorConstructorBean.class, config);
        CreatorFactoryBean creatorFactory = JSON.parseObject(
                "{\"name\":\"factory\",\"number\":4}", CreatorFactoryBean.class, config);
        BuilderBackedBean builderBacked = JSON.parseObject("{\"name\":\"built\"}", BuilderBackedBean.class, config);
        SingleValueFactoryBean singleValue = parseWithDeserializer("\"scalar\"", SingleValueFactoryBean.class, config);

        assertThat(creatorConstructor.name).isEqualTo("constructor");
        assertThat(creatorConstructor.number).isEqualTo(3);
        assertThat(creatorFactory.name).isEqualTo("factory");
        assertThat(creatorFactory.number).isEqualTo(4);
        assertThat(builderBacked.name).isEqualTo("built");
        assertThat(singleValue.value).isEqualTo("scalar");
    }

    @Test
    void castToJavaBeanCreatesBeansFromMapsWithFieldsCreatorsFactoriesAndBuilders() {
        ParserConfig config = noAsmConfig();
        Map<String, Object> fieldValues = new LinkedHashMap<>();
        fieldValues.put("name", "field");
        fieldValues.put("reference", new ReferencedValue("direct"));

        MapBackedFieldsBean fieldsBean = TypeUtils.castToJavaBean(fieldValues, MapBackedFieldsBean.class, config);
        CreatorConstructorBean creatorConstructor = TypeUtils.castToJavaBean(
                mapOf("name", "constructor", "number", 5), CreatorConstructorBean.class, config);
        CreatorFactoryBean creatorFactory = TypeUtils.castToJavaBean(
                mapOf("name", "factory", "number", 6), CreatorFactoryBean.class, config);
        BuilderBackedBean builderBacked = TypeUtils.castToJavaBean(
                mapOf("name", "builtFromMap"), BuilderBackedBean.class, config);

        assertThat(fieldsBean.name).isEqualTo("field");
        assertThat(fieldsBean.reference.label).isEqualTo("direct");
        assertThat(creatorConstructor.name).isEqualTo("constructor");
        assertThat(creatorConstructor.number).isEqualTo(5);
        assertThat(creatorFactory.name).isEqualTo("factory");
        assertThat(creatorFactory.number).isEqualTo(6);
        assertThat(builderBacked.name).isEqualTo("builtFromMap");
    }

    @Test
    void parseFieldSupportsNonPublicFieldsAndUnwrappedFieldMapAndMethodProperties() {
        ParserConfig config = noAsmConfig();

        PrivateFieldBean privateField = JSON.parseObject(
                "{\"hidden\":\"visible\"}", PrivateFieldBean.class, config, Feature.SupportNonPublicField);
        UnwrappedNestedContainer nested = JSON.parseObject("{\"nestedName\":\"inner\"}",
                UnwrappedNestedContainer.class, config);
        UnwrappedMapContainer map = JSON.parseObject("{\"dynamic\":12}", UnwrappedMapContainer.class, config);
        UnwrappedMethodContainer method = JSON.parseObject("{\"methodValue\":true}",
                UnwrappedMethodContainer.class, config);

        assertThat(privateField.getHidden()).isEqualTo("visible");
        assertThat(nested.nested.nestedName).isEqualTo("inner");
        assertThat(map.values).containsEntry("dynamic", 12);
        assertThat(method.values).containsEntry("methodValue", true);
    }

    @Test
    void customBeanInfoMapCreationSetsPrimitiveFieldsDirectly() throws Exception {
        ParserConfig config = noAsmConfig();
        JavaBeanDeserializer deserializer = customFieldDeserializer(PrimitiveDirectFieldsBean.class,
                "falseValue", "trueValue", "intValue", "longValue", "floatNumber", "floatString",
                "doubleNumber", "doubleString");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("falseValue", Boolean.FALSE);
        values.put("trueValue", Boolean.TRUE);
        values.put("intValue", 11);
        values.put("longValue", 12L);
        values.put("floatNumber", 1.25F);
        values.put("floatString", "2.5");
        values.put("doubleNumber", 3.75D);
        values.put("doubleString", "4.5");

        PrimitiveDirectFieldsBean bean = (PrimitiveDirectFieldsBean) deserializer.createInstance(values, config);

        assertThat(bean.falseValue).isFalse();
        assertThat(bean.trueValue).isTrue();
        assertThat(bean.intValue).isEqualTo(11);
        assertThat(bean.longValue).isEqualTo(12L);
        assertThat(bean.floatNumber).isEqualTo(1.25F);
        assertThat(bean.floatString).isEqualTo(2.5F);
        assertThat(bean.doubleNumber).isEqualTo(3.75D);
        assertThat(bean.doubleString).isEqualTo(4.5D);
    }

    @Test
    void customBeanInfoUsesDefaultConstructorWhenKotlinCreatorStringArgumentIsMissing() throws Exception {
        ParserConfig config = noAsmConfig();
        JavaBeanDeserializer deserializer = customKotlinLikeDeserializer();
        Map<String, Object> values = mapOf("number", 8);

        KotlinLikeBean fromMap = (KotlinLikeBean) deserializer.createInstance(values, config);
        KotlinLikeBean fromJson = parseWithDeserializer("{\"number\":9}", KotlinLikeBean.class, deserializer, config);

        assertThat(fromMap.name).isEqualTo("default");
        assertThat(fromMap.number).isEqualTo(8);
        assertThat(fromJson.name).isEqualTo("default");
        assertThat(fromJson.number).isEqualTo(9);
    }

    private static ParserConfig noAsmConfig() {
        ParserConfig config = new ParserConfig();
        config.setAsmEnable(false);
        return config;
    }

    private static <T> T parseWithDeserializer(String json, Class<T> beanClass, ParserConfig config) {
        ObjectDeserializer deserializer = config.getDeserializer(beanClass);
        return parseWithDeserializer(json, beanClass, deserializer, config);
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

    private static JavaBeanDeserializer customFieldDeserializer(Class<?> beanClass, String... fieldNames)
            throws NoSuchFieldException, NoSuchMethodException {
        Constructor<?> defaultConstructor = beanClass.getConstructor();
        List<FieldInfo> fields = new ArrayList<>();
        for (String fieldName : fieldNames) {
            Field field = beanClass.getField(fieldName);
            fields.add(new FieldInfo(fieldName, null, field.getType(), field.getGenericType(), field, 0, 0, 0));
        }
        JavaBeanInfo beanInfo = new JavaBeanInfo(beanClass, null, defaultConstructor, null, null, null, null, fields);
        return new JavaBeanDeserializer(noAsmConfig(), beanInfo);
    }

    private static JavaBeanDeserializer customKotlinLikeDeserializer()
            throws NoSuchFieldException, NoSuchMethodException {
        Constructor<KotlinLikeBean> defaultConstructor = KotlinLikeBean.class.getConstructor();
        Constructor<KotlinLikeBean> creatorConstructor = KotlinLikeBean.class.getConstructor(String.class, int.class);
        List<FieldInfo> fields = new ArrayList<>();
        fields.add(kotlinLikeFieldInfo("name", String.class));
        fields.add(kotlinLikeFieldInfo("number", int.class));
        JavaBeanInfo beanInfo = new JavaBeanInfo(
                KotlinLikeBean.class, null, defaultConstructor, creatorConstructor, null, null, null, fields);
        beanInfo.kotlin = true;
        beanInfo.creatorConstructorParameters = new String[] {"name", "number"};
        beanInfo.kotlinDefaultConstructor = defaultConstructor;
        return new JavaBeanDeserializer(noAsmConfig(), beanInfo);
    }

    private static FieldInfo kotlinLikeFieldInfo(String name, Class<?> fieldClass) throws NoSuchFieldException {
        Field field = KotlinLikeBean.class.getField(name);
        return new FieldInfo(name, KotlinLikeBean.class, fieldClass, field.getGenericType(), field, 0, 0, 0);
    }

    private static Map<String, Object> mapOf(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, Object> values = new HashMap<>();
        values.put(firstKey, firstValue);
        values.put(secondKey, secondValue);
        return values;
    }

    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> values = new HashMap<>();
        values.put(key, value);
        return values;
    }

    @JSONType(autoTypeCheckHandler = RecordingAutoTypeCheckHandler.class)
    public static class AutoTypeCheckedBean {
        public String name;
    }

    public static class RecordingAutoTypeCheckHandler implements ParserConfig.AutoTypeCheckHandler {
        static boolean constructed;

        public RecordingAutoTypeCheckHandler() {
            constructed = true;
        }

        @Override
        public Class<?> handler(String typeName, Class<?> expectClass, int features) {
            return null;
        }
    }

    public interface ProxyView {
        String getName();
    }

    public static class FactoryDefaultBean {
        public final String source;

        private FactoryDefaultBean(String source) {
            this.source = source;
        }

        @JSONCreator
        public static FactoryDefaultBean create() {
            return new FactoryDefaultBean("factory");
        }
    }

    public static class OuterBean {
        public InnerBean inner;

        public class InnerBean {
            public int value;
        }
    }

    public static class CreatorConstructorBean {
        public final String name;
        public final int number;

        @JSONCreator
        public CreatorConstructorBean(@JSONField(name = "name") String name, @JSONField(name = "number") int number) {
            this.name = name;
            this.number = number;
        }
    }

    public static class CreatorFactoryBean {
        public final String name;
        public final int number;

        private CreatorFactoryBean(String name, int number) {
            this.name = name;
            this.number = number;
        }

        @JSONCreator
        public static CreatorFactoryBean create(
                @JSONField(name = "name") String name, @JSONField(name = "number") int number) {
            return new CreatorFactoryBean(name, number);
        }
    }

    public static class SingleValueFactoryBean {
        public final String value;

        private SingleValueFactoryBean(String value) {
            this.value = value;
        }

        @JSONCreator
        public static SingleValueFactoryBean create(@JSONField(name = "value") String value) {
            return new SingleValueFactoryBean(value);
        }
    }

    @JSONType(builder = BuilderBackedBean.Builder.class)
    public static class BuilderBackedBean {
        public final String name;

        private BuilderBackedBean(String name) {
            this.name = name;
        }

        @JSONPOJOBuilder
        public static class Builder {
            private String name;

            public Builder withName(String name) {
                this.name = name;
                return this;
            }

            public BuilderBackedBean build() {
                return new BuilderBackedBean(name);
            }
        }
    }

    public static class ReferencedValue {
        public final String label;

        public ReferencedValue(String label) {
            this.label = label;
        }
    }

    public static class MapBackedFieldsBean {
        public String name;
        public ReferencedValue reference;
    }

    public static class PrivateFieldBean {
        private String hidden;

        public String getHidden() {
            return hidden;
        }
    }

    public static class PrimitiveDirectFieldsBean {
        public boolean falseValue = true;
        public boolean trueValue;
        public int intValue;
        public long longValue;
        public float floatNumber;
        public float floatString;
        public double doubleNumber;
        public double doubleString;
    }

    public static class KotlinLikeBean {
        public String name;
        public int number;

        public KotlinLikeBean() {
            this.name = "default";
        }

        public KotlinLikeBean(String name, int number) {
            this.name = name;
            this.number = number;
        }
    }

    public static class NestedValueBean {
        public String nestedName;
    }

    public static class UnwrappedNestedContainer {
        @JSONField(unwrapped = true)
        public NestedValueBean nested = new NestedValueBean();
    }

    public static class UnwrappedMapContainer {
        @JSONField(unwrapped = true)
        public Map<String, Object> values = new LinkedHashMap<>();
    }

    public static class UnwrappedMethodContainer {
        public Map<String, Object> values = new LinkedHashMap<>();

        @JSONField(unwrapped = true)
        public void put(String key, Object value) {
            values.put(key, value);
        }
    }
}
