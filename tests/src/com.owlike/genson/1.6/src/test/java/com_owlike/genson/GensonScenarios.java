/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_owlike.genson;

import static org.assertj.core.api.Assertions.assertThat;

import com.owlike.genson.BeanView;
import com.owlike.genson.Context;
import com.owlike.genson.Converter;
import com.owlike.genson.GenericType;
import com.owlike.genson.Genson;
import com.owlike.genson.GensonBuilder;
import com.owlike.genson.Operations;
import com.owlike.genson.annotation.JsonConverter;
import com.owlike.genson.annotation.JsonCreator;
import com.owlike.genson.annotation.JsonProperty;
import com.owlike.genson.ext.jaxb.JAXBBundle;
import com.owlike.genson.reflect.BeanMutatorAccessorResolver.StandardMutaAccessorResolver;
import com.owlike.genson.stream.ObjectReader;
import com.owlike.genson.stream.ObjectWriter;
import java.util.Arrays;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

final class GensonScenarios {
    private GensonScenarios() { }

    static void roundTripMixedBean() {
        Genson genson = new Genson();
        MixedBean source = new MixedBean();
        source.fieldValue = "field";
        source.setMethodValue("method");

        String json = genson.serialize(source);
        MixedBean restored = genson.deserialize(json, MixedBean.class);

        assertThat(restored.fieldValue).isEqualTo("field");
        assertThat(restored.getMethodValue()).isEqualTo("method");
    }

    static void deserializeLargeArray() {
        String json = """
                ["0","1","2","3","4","5","6","7","8","9","10","11"]
                """;
        String[] values = new Genson().deserialize(json, String[].class);

        assertThat(values).hasSize(12);
        assertThat(values[11]).isEqualTo("11");
    }

    static void roundTripEnum() {
        Genson genson = new Genson();

        assertThat(genson.serialize(Color.BLUE)).isEqualTo("\"BLUE\"");
        assertThat(genson.deserialize("\"RED\"", Color.class)).isEqualTo(Color.RED);
    }

    static void roundTripConvertedProperty() {
        Genson genson = new Genson();
        ConvertedBean source = new ConvertedBean();
        source.code = "mixedCase";

        String json = genson.serialize(source);
        ConvertedBean restored = genson.deserialize(json, ConvertedBean.class);

        assertThat(json).contains("MIXEDCASE");
        assertThat(restored.code).isEqualTo("mixedcase");
    }

    static void deserializeWithConstructorCreator() {
        ConstructorBean value = new Genson().deserialize("{\"name\":\"Grace\"}", ConstructorBean.class);

        assertThat(value.name).isEqualTo("Grace");
    }

    static void deserializeWithMethodCreator() {
        MethodBean value = new Genson().deserialize("{\"name\":\"Linus\"}", MethodBean.class);

        assertThat(value.name).isEqualTo("Linus");
    }

    static void serializeField() {
        FieldBean source = new FieldBean();
        source.value = "visible";

        assertThat(new GensonBuilder().useMethods(false).create().serialize(source))
                .isEqualTo("{\"value\":\"visible\"}");
    }

    static void deserializeField() {
        FieldBean value =
                new GensonBuilder().useMethods(false).create().deserialize("{\"value\":\"changed\"}", FieldBean.class);

        assertThat(value.value).isEqualTo("changed");
    }

    static void serializeMethod() {
        MethodOnlyBean source = new MethodOnlyBean();
        source.setValue("visible");

        assertThat(new GensonBuilder().useFields(false).create().serialize(source))
                .isEqualTo("{\"value\":\"visible\"}");
    }

    static void deserializeMethod() {
        MethodOnlyBean value =
                new GensonBuilder()
                        .useFields(false)
                        .create()
                        .deserialize("{\"value\":\"changed\"}", MethodOnlyBean.class);

        assertThat(value.getValue()).isEqualTo("changed");
    }

    static void serializeWithBeanView() {
        Genson genson = new GensonBuilder().useBeanViews(true).create();
        ViewedPerson person = new ViewedPerson("Ada");

        assertThat(genson.serialize(person, PersonView.class)).isEqualTo("{\"displayName\":\"ADA\"}");
    }

    static void deserializeWithBeanView() {
        Genson genson = new GensonBuilder().useBeanViews(true).create();
        ViewedPerson person =
                genson.deserialize("{\"displayName\":\"GRACE\"}", ViewedPerson.class, PersonView.class);

        assertThat(person.name).isEqualTo("grace");
    }

    static void resolveClassName() throws ClassNotFoundException {
        Genson genson = new Genson();

        assertThat(genson.classFor("java.util.ArrayList")).isEqualTo(java.util.ArrayList.class);
    }

    static void combineArrays() {
        String[] combined = Operations.union(String[].class, new String[] {"a", "b"}, new String[] {"c"});

        assertThat(combined).containsExactly("a", "b", "c");
    }

    static void roundTripJaxbBean() {
        Genson genson = new GensonBuilder().withBundle(new JAXBBundle()).create();
        JaxbBean source = new JaxbBean();
        source.setValue("jaxb");

        String json = genson.serialize(source);
        JaxbBean restored = genson.deserialize(json, JaxbBean.class);

        assertThat(json).contains("renamed");
        assertThat(restored.getValue()).isEqualTo("jaxb");
    }

    static void roundTripJaxbEnum() {
        Genson genson = new GensonBuilder().withBundle(new JAXBBundle()).create();

        assertThat(genson.serialize(JaxbColor.GREEN)).isEqualTo("\"green-value\"");
        assertThat(genson.deserialize("\"green-value\"", JaxbColor.class)).isEqualTo(JaxbColor.GREEN);
    }

    static void honorJaxbTransientAccessor() {
        Genson genson = new GensonBuilder().withBundle(new JAXBBundle()).create();
        TransientJaxbBean source = new TransientJaxbBean();
        source.setVisible("shown");
        source.setSecret("hidden");

        String json = genson.serialize(source);
        TransientJaxbBean restored =
                genson.deserialize("{\"visible\":\"changed\",\"secret\":\"ignored\"}", TransientJaxbBean.class);

        assertThat(json).contains("visible").doesNotContain("secret");
        assertThat(restored.getVisible()).isEqualTo("changed");
        assertThat(restored.getSecret()).isNull();
    }

    static void roundTripJaxbAdaptedProperty() {
        Genson genson = new GensonBuilder().withBundle(new JAXBBundle()).create();
        AdaptedJaxbBean source = new AdaptedJaxbBean();
        source.code = new AdaptedCode("alpha");

        String json = genson.serialize(source);
        AdaptedJaxbBean restored = genson.deserialize(json, AdaptedJaxbBean.class);

        assertThat(json).isEqualTo("{\"code\":\"code:alpha\"}");
        assertThat(restored.code.value).isEqualTo("alpha");
    }

    static void deserializeResolverUsingDebugParameterResolver() {
        Genson debugInfoGenson = new GensonBuilder().useConstructorWithArguments(true).create();

        StandardMutaAccessorResolver resolver =
                debugInfoGenson.deserialize("{}", StandardMutaAccessorResolver.class);
        Genson genson = new GensonBuilder().set(resolver).create();
        FieldBean source = new FieldBean();
        source.value = "resolved";

        String json = genson.serialize(source);
        FieldBean restored = genson.deserialize(json, FieldBean.class);

        assertThat(json).isEqualTo("{\"value\":\"resolved\"}");
        assertThat(restored.value).isEqualTo("resolved");
    }

    static void resolveGenericArrayType() {
        Genson genson = new Genson();
        GenericArrayBean<String> source = new GenericArrayBean<String>();
        source.values = new String[] {"a", "b"};
        GenericType<GenericArrayBean<String>> type = new GenericType<GenericArrayBean<String>>() {};

        String json = genson.serialize(source, type);
        GenericArrayBean<String> restored = genson.deserialize(json, type);

        assertThat(Arrays.asList(restored.values)).containsExactly("a", "b");
    }

    public enum Color {
        RED,
        BLUE
    }

    public static class MixedBean {
        public String fieldValue;
        private String methodValue;

        public String getMethodValue() {
            return methodValue;
        }

        public void setMethodValue(String methodValue) {
            this.methodValue = methodValue;
        }

        @JsonCreator
        public static MixedBean create() {
            return new MixedBean();
        }
    }

    public static class FieldBean {
        public String value;
    }

    public static class MethodOnlyBean {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class ConstructorBean {
        final String name;

        @JsonCreator
        public ConstructorBean(@JsonProperty("name") String name) {
            this.name = name;
        }
    }

    public static class MethodBean {
        final String name;

        private MethodBean(String name) {
            this.name = name;
        }

        @JsonCreator
        public static MethodBean create(@JsonProperty("name") String name) {
            return new MethodBean(name);
        }
    }

    public static class ConvertedBean {
        @JsonConverter(UpperCaseConverter.class)
        public String code;
    }

    public static class UpperCaseConverter implements Converter<String> {
        public UpperCaseConverter() { }

        @Override
        public void serialize(String object, ObjectWriter writer, Context ctx) {
            writer.writeValue(object.toUpperCase());
        }

        @Override
        public String deserialize(ObjectReader reader, Context ctx) {
            return reader.valueAsString().toLowerCase();
        }
    }

    public static class ViewedPerson {
        String name;

        public ViewedPerson() { }

        ViewedPerson(String name) {
            this.name = name;
        }
    }

    public static class PersonView implements BeanView<ViewedPerson> {
        public PersonView() { }

        @JsonCreator
        public static ViewedPerson create() {
            return new ViewedPerson();
        }

        public String getDisplayName(ViewedPerson person) {
            return person.name.toUpperCase();
        }

        public void setDisplayName(String name, ViewedPerson person) {
            person.name = name.toLowerCase();
        }
    }

    public static class JaxbBean {
        private String value;

        @XmlElement(name = "renamed")
        public String getValue() {
            return value;
        }

        @XmlElement(name = "renamed")
        public void setValue(String value) {
            this.value = value;
        }
    }

    public enum JaxbColor {
        @XmlEnumValue("green-value")
        GREEN,
        RED
    }

    public static class TransientJaxbBean {
        private String visible;
        private String secret;

        public String getVisible() {
            return visible;
        }

        public void setVisible(String visible) {
            this.visible = visible;
        }

        @XmlTransient
        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class AdaptedJaxbBean {
        @XmlJavaTypeAdapter(AdaptedCodeAdapter.class)
        public AdaptedCode code;
    }

    public static class AdaptedCode {
        final String value;

        public AdaptedCode(String value) {
            this.value = value;
        }
    }

    public static class AdaptedCodeAdapter extends XmlAdapter<String, AdaptedCode> {
        public AdaptedCodeAdapter() { }

        @Override
        public AdaptedCode unmarshal(String value) {
            return new AdaptedCode(value.substring("code:".length()));
        }

        @Override
        public String marshal(AdaptedCode value) {
            return "code:" + value.value;
        }
    }

    public static class GenericArrayBean<T> {
        public T[] values;
    }
}
