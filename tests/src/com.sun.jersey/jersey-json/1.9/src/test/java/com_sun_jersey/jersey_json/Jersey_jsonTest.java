/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.api.json.JSONJAXBContext;
import com.sun.jersey.api.json.JSONMarshaller;
import com.sun.jersey.api.json.JSONUnmarshaller;
import com.sun.jersey.api.json.JSONWithPadding;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class Jersey_jsonTest {
    static {
        System.setProperty("com.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize", "true");
    }

    @Test
    void mappedConfigurationControlsJsonOutputAndInput() throws Exception {
        JSONConfiguration configuration = JSONConfiguration.mapped()
                .nonStrings("number")
                .arrays("titles")
                .rootUnwrapping(true)
                .build();
        JSONJAXBContext context = new JSONJAXBContext(configuration, BeanOne.class);

        BeanOne bean = new BeanOne("Howard", 3);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        context.createJSONMarshaller().marshallToJSON(bean, outputStream);

        String json = outputStream.toString(StandardCharsets.UTF_8.name());
        assertThat(json).contains("\"name\":\"Howard\"");
        assertThat(json).contains("\"number\":3");
        assertThat(json).doesNotContain("\"number\":\"3\"");
        assertThat(json).doesNotContain("beanOne");

        ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        BeanOne unmarshalled = context.createJSONUnmarshaller().unmarshalFromJSON(inputStream, BeanOne.class);
        assertThat(unmarshalled.name).isEqualTo("Howard");
        assertThat(unmarshalled.number).isEqualTo(3);
        assertThat(context.getJSONConfiguration()).isSameAs(configuration);
    }

    @Test
    void naturalConfigurationRoundTripsCollectionsWithJaxbMarshallerAdapters() throws Exception {
        JSONConfiguration configuration = JSONConfiguration.natural()
                .rootUnwrapping(true)
                .build();
        JSONJAXBContext context = new JSONJAXBContext(configuration, BeanTwo.class);

        Marshaller marshaller = context.createMarshaller();
        assertThat(marshaller).isInstanceOf(JSONMarshaller.class);
        JSONMarshaller jsonMarshaller = JSONJAXBContext.getJSONMarshaller(marshaller);
        assertThat(jsonMarshaller).isSameAs(marshaller);

        BeanTwo bean = new BeanTwo(Arrays.asList("Title1", "Title2"));
        StringWriter writer = new StringWriter();
        jsonMarshaller.marshallToJSON(bean, writer);

        String json = writer.toString();
        assertThat(json).contains("\"titles\":[\"Title1\",\"Title2\"]");
        assertThat(json).doesNotContain("beanTwo");

        Unmarshaller unmarshaller = context.createUnmarshaller();
        assertThat(unmarshaller).isInstanceOf(JSONUnmarshaller.class);
        JSONUnmarshaller jsonUnmarshaller = JSONJAXBContext.getJSONUnmarshaller(unmarshaller);
        assertThat(jsonUnmarshaller).isSameAs(unmarshaller);

        BeanTwo unmarshalled = jsonUnmarshaller.unmarshalFromJSON(new StringReader(json), BeanTwo.class);
        assertThat(unmarshalled.titles).containsExactly("Title1", "Title2");

        JAXBElement<BeanTwo> element = jsonUnmarshaller.unmarshalJAXBElementFromJSON(
                new StringReader(json), BeanTwo.class);
        assertThat(element.getName().getLocalPart()).isEqualTo("beanTwo");
        assertThat(element.getValue().titles).containsExactly("Title1", "Title2");
    }

    @Test
    void configurationBuildersExposeImmutableNotationSpecificOptions() {
        Map<String, String> namespaces = new LinkedHashMap<>();
        namespaces.put("urn:example", "ex");

        JSONConfiguration mapped = JSONConfiguration.mapped()
                .arrays("item", "title")
                .attributeAsElement("id")
                .nonStrings("number", "active")
                .xml2JsonNs(namespaces)
                .nsSeparator('_')
                .rootUnwrapping(false)
                .build();

        assertThat(JSONConfiguration.FEATURE_POJO_MAPPING)
                .isEqualTo("com.sun.jersey.api.json.POJOMappingFeature");
        assertThat(JSONConfiguration.DEFAULT.getNotation()).isEqualTo(JSONConfiguration.Notation.MAPPED);
        assertThat(mapped.getNotation()).isEqualTo(JSONConfiguration.Notation.MAPPED);
        assertThat(mapped.getArrays()).containsExactlyInAnyOrder("item", "title");
        assertThat(mapped.getAttributeAsElements()).containsExactly("id");
        assertThat(mapped.getNonStrings()).containsExactlyInAnyOrder("number", "active");
        assertThat(mapped.getXml2JsonNs()).containsEntry("urn:example", "ex");
        assertThat(mapped.getNsSeparator()).isEqualTo('_');
        assertThat(mapped.isRootUnwrapping()).isFalse();
        assertThat(mapped.toString()).contains("notation:MAPPED", "rootStripping:false");
        assertThatThrownBy(() -> mapped.getArrays().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);

        JSONConfiguration formatted = JSONConfiguration.createJSONConfigurationWithFormatted(mapped, true);
        assertThat(formatted).isNotSameAs(mapped);
        assertThat(formatted.isHumanReadableFormatting()).isTrue();
        assertThat(formatted.getArrays()).containsExactlyInAnyOrder("item", "title");

        JSONConfiguration unwrapped = JSONConfiguration.createJSONConfigurationWithRootUnwrapping(formatted, true);
        assertThat(unwrapped.isRootUnwrapping()).isTrue();
        assertThat(JSONConfiguration.copyBuilder(unwrapped).build().getXml2JsonNs())
                .containsEntry("urn:example", "ex");

        JSONConfiguration natural = JSONConfiguration.natural()
                .humanReadableFormatting(true)
                .usePrefixesAtNaturalAttributes()
                .build();
        assertThat(natural.getNotation()).isEqualTo(JSONConfiguration.Notation.NATURAL);
        assertThat(natural.isHumanReadableFormatting()).isTrue();
        assertThat(natural.isUsingPrefixesAtNaturalAttributes()).isTrue();

        JSONConfiguration mappedJettison = JSONConfiguration.mappedJettison()
                .xml2JsonNs(namespaces)
                .build();
        assertThat(mappedJettison.getNotation()).isEqualTo(JSONConfiguration.Notation.MAPPED_JETTISON);
        assertThat(mappedJettison.isRootUnwrapping()).isFalse();

        JSONConfiguration badgerFish = JSONConfiguration.badgerFish().build();
        assertThat(badgerFish.getNotation()).isEqualTo(JSONConfiguration.Notation.BADGERFISH);
        assertThat(badgerFish.isRootUnwrapping()).isFalse();
    }

    @Test
    void jsonWithPaddingDelegatesJacksonSerializationToJsonSource() throws Exception {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("status", "ok");
        source.put("count", 2);

        JSONWithPadding defaultPadding = new JSONWithPadding(source, null);
        assertThat(defaultPadding.getCallbackName()).isEqualTo(JSONWithPadding.DEFAULT_CALLBACK_NAME);
        assertThat(defaultPadding.getJsonSource()).isSameAs(source);

        JSONWithPadding customPadding = new JSONWithPadding(source, "updateDashboard");
        assertThat(customPadding.getCallbackName()).isEqualTo("updateDashboard");

        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.writeValueAsString(customPadding)).isEqualTo("{\"status\":\"ok\",\"count\":2}");
        assertThatThrownBy(() -> new JSONWithPadding(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON source");
    }

    @Test
    void badgerFishNotationPreservesAttributesAndTextValues() throws Exception {
        JSONConfiguration configuration = JSONConfiguration.badgerFish().build();
        JSONJAXBContext context = new JSONJAXBContext(configuration, BeanThree.class);

        BeanThree bean = new BeanThree("BK-101", "Graph Theory");
        StringWriter writer = new StringWriter();
        context.createJSONMarshaller().marshallToJSON(bean, writer);

        String json = writer.toString();
        assertThat(json).contains("\"beanThree\"");
        assertThat(json).contains("\"@code\":\"BK-101\"");
        assertThat(json).contains("\"title\"");
        assertThat(json).contains("\"$\":\"Graph Theory\"");

        BeanThree unmarshalled = context.createJSONUnmarshaller()
                .unmarshalFromJSON(new StringReader(json), BeanThree.class);
        assertThat(unmarshalled.code).isEqualTo("BK-101");
        assertThat(unmarshalled.title).isEqualTo("Graph Theory");
    }

    @XmlRootElement(name = "beanOne")
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"name", "number"})
    public static class BeanOne {
        @XmlElement
        public String name;

        @XmlElement
        public int number;

        public BeanOne() {
        }

        public BeanOne(String name, int number) {
            this.name = name;
            this.number = number;
        }
    }

    @XmlRootElement(name = "beanTwo")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class BeanTwo {
        public List<String> titles = new ArrayList<>();

        public BeanTwo() {
        }

        public BeanTwo(List<String> titles) {
            this.titles = new ArrayList<>(titles);
        }
    }

    @XmlRootElement(name = "beanThree")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class BeanThree {
        @XmlAttribute
        public String code;

        @XmlElement
        public String title;

        public BeanThree() {
        }

        public BeanThree(String code, String title) {
            this.code = code;
            this.title = title;
        }
    }
}
