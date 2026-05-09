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
import java.util.Objects;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class Jersey_jsonTest {

    @BeforeAll
    static void disableJaxbRuntimeBytecodeGeneration() {
        System.setProperty("com.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize", "true");
    }

    @Test
    void configurationBuildersExposeSelectedNotationOptions() {
        Map<String, String> namespaces = new LinkedHashMap<>();
        namespaces.put("http://example.test/books", "bk");
        namespaces.put("http://example.test/meta", "meta");

        JSONConfiguration mapped = JSONConfiguration.mapped()
                .arrays("tag", "author")
                .attributeAsElement("isbn")
                .nonStrings("pages", "available")
                .xml2JsonNs(namespaces)
                .nsSeparator('_')
                .rootUnwrapping(true)
                .build();

        assertThat(mapped.getNotation()).isEqualTo(JSONConfiguration.Notation.MAPPED);
        assertThat(mapped.getArrays()).containsExactlyInAnyOrder("tag", "author");
        assertThat(mapped.getAttributeAsElements()).containsExactly("isbn");
        assertThat(mapped.getNonStrings()).containsExactlyInAnyOrder("pages", "available");
        assertThat(mapped.getXml2JsonNs()).containsEntry("http://example.test/books", "bk");
        assertThat(mapped.getNsSeparator()).isEqualTo('_');
        assertThat(mapped.isRootUnwrapping()).isTrue();
        assertThat(mapped.toString()).contains("MAPPED");

        JSONConfiguration mappedJettison = JSONConfiguration.mappedJettison()
                .xml2JsonNs(namespaces)
                .build();
        assertThat(mappedJettison.getNotation()).isEqualTo(JSONConfiguration.Notation.MAPPED_JETTISON);
        assertThat(mappedJettison.getXml2JsonNs()).containsEntry("http://example.test/meta", "meta");

        JSONConfiguration badgerFish = JSONConfiguration.badgerFish().build();
        assertThat(badgerFish.getNotation()).isEqualTo(JSONConfiguration.Notation.BADGERFISH);
    }

    @Test
    void configurationCopyAndFactoryMethodsPreserveIndependentSettings() {
        JSONConfiguration natural = JSONConfiguration.natural()
                .humanReadableFormatting(true)
                .rootUnwrapping(false)
                .usePrefixesAtNaturalAttributes()
                .build();

        JSONConfiguration compact = JSONConfiguration.createJSONConfigurationWithFormatted(natural, false);
        JSONConfiguration unwrapped = JSONConfiguration.createJSONConfigurationWithRootUnwrapping(compact, true);
        JSONConfiguration copy = JSONConfiguration.copyBuilder(unwrapped).build();

        assertThat(natural.getNotation()).isEqualTo(JSONConfiguration.Notation.NATURAL);
        assertThat(natural.isHumanReadableFormatting()).isTrue();
        assertThat(natural.isRootUnwrapping()).isFalse();
        assertThat(natural.isUsingPrefixesAtNaturalAttributes()).isTrue();
        assertThat(compact.isHumanReadableFormatting()).isFalse();
        assertThat(compact.isRootUnwrapping()).isFalse();
        assertThat(unwrapped.isRootUnwrapping()).isTrue();
        assertThat(copy.getNotation()).isEqualTo(JSONConfiguration.Notation.NATURAL);
        assertThat(copy.isHumanReadableFormatting()).isFalse();
        assertThat(copy.isRootUnwrapping()).isTrue();
        assertThat(copy.isUsingPrefixesAtNaturalAttributes()).isTrue();

        assertThatThrownBy(() -> JSONConfiguration.createJSONConfigurationWithFormatted(null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void naturalJsonContextMarshalsAndUnmarshalsBeanWithWriterAndReader() throws JAXBException {
        Book book = new Book("978-0134685991", "Effective Java", 416, true, Arrays.asList("java", "api"));
        JSONConfiguration configuration = JSONConfiguration.natural()
                .humanReadableFormatting(true)
                .rootUnwrapping(false)
                .build();
        JSONJAXBContext context = new JSONJAXBContext(configuration, Book.class);

        StringWriter json = new StringWriter();
        context.createJSONMarshaller().marshallToJSON(book, json);

        assertThat(context.getJSONConfiguration()).isSameAs(configuration);
        assertThat(json.toString()).contains("book", "isbn", "Effective Java", "tag");

        Book restored = context.createJSONUnmarshaller().unmarshalFromJSON(new StringReader(json.toString()), Book.class);
        assertThat(restored).isEqualTo(book);
    }

    @Test
    void mappedJsonContextHonorsArrayNonStringAndAttributeElementMappings() throws Exception {
        Book book = new Book("978-1491950357", "Building Microservices", 280, true, Arrays.asList("architecture"));
        JSONConfiguration configuration = JSONConfiguration.mapped()
                .arrays("tag")
                .attributeAsElement("isbn")
                .nonStrings("pages", "available")
                .rootUnwrapping(false)
                .build();
        JSONJAXBContext context = new JSONJAXBContext(configuration, Book.class);

        StringWriter json = new StringWriter();
        context.createJSONMarshaller().marshallToJSON(book, json);

        Map<?, ?> document = new ObjectMapper().readValue(json.toString(), Map.class);
        assertThat(document).hasSize(1);
        assertThat(document.containsKey("book")).isTrue();
        assertThat(document.get("book")).isInstanceOf(Map.class);
        Map<?, ?> bookJson = (Map<?, ?>) document.get("book");
        assertThat(bookJson.get("isbn")).isEqualTo(book.isbn);
        assertThat(bookJson.containsKey("@isbn")).isFalse();
        assertThat(bookJson.get("title")).isEqualTo(book.title);
        assertThat(bookJson.get("pages")).isEqualTo(book.pages);
        assertThat(bookJson.get("available")).isEqualTo(book.available);
        assertThat(bookJson.get("tag")).isEqualTo(Arrays.asList("architecture"));

        Book restored = context.createJSONUnmarshaller().unmarshalFromJSON(new StringReader(json.toString()), Book.class);
        assertThat(restored).isEqualTo(book);
    }

    @Test
    void jsonMarshallerAndUnmarshallerAdaptersSupportStreamRoundTrip() throws JAXBException {
        Book book = new Book("978-0321356680", "Java Concurrency in Practice", 384, false,
                Arrays.asList("threads", "testing"));
        JSONJAXBContext context = new JSONJAXBContext(JSONConfiguration.natural().rootUnwrapping(true).build(), Book.class);

        Marshaller marshaller = context.createMarshaller();
        JSONMarshaller jsonMarshaller = JSONJAXBContext.getJSONMarshaller(marshaller);
        jsonMarshaller.setProperty(JSONMarshaller.FORMATTED, Boolean.TRUE);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        jsonMarshaller.marshallToJSON(book, output);

        String json = output.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("Java Concurrency in Practice");
        assertThat(json).doesNotContain("\"book\"");

        Unmarshaller unmarshaller = context.createUnmarshaller();
        JSONUnmarshaller jsonUnmarshaller = JSONJAXBContext.getJSONUnmarshaller(unmarshaller);
        ByteArrayInputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        Book restored = jsonUnmarshaller.unmarshalFromJSON(input, Book.class);

        assertThat(jsonMarshaller).isSameAs(marshaller);
        assertThat(jsonUnmarshaller).isSameAs(unmarshaller);
        assertThat(restored).isEqualTo(book);
    }

    @Test
    void jsonWithPaddingStoresCallbackAndDelegatesSourceSerialization() throws Exception {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("status", "ok");
        source.put("count", 2);

        JSONWithPadding explicitCallback = new JSONWithPadding(source, "handleBooks");
        JSONWithPadding defaultCallback = new JSONWithPadding(source, null);

        assertThat(explicitCallback.getCallbackName()).isEqualTo("handleBooks");
        assertThat(explicitCallback.getJsonSource()).isSameAs(source);
        assertThat(defaultCallback.getCallbackName()).isEqualTo(JSONWithPadding.DEFAULT_CALLBACK_NAME);
        assertThat(new ObjectMapper().writeValueAsString(explicitCallback))
                .isEqualTo("{\"status\":\"ok\",\"count\":2}");
        assertThatThrownBy(() -> new JSONWithPadding(null, "callback"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @XmlRootElement(name = "book")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Book {
        @XmlAttribute
        public String isbn;

        @XmlElement
        public String title;

        @XmlElement
        public int pages;

        @XmlElement
        public boolean available;

        @XmlElement(name = "tag")
        public List<String> tags = new ArrayList<>();

        public Book() {
        }

        Book(String isbn, String title, int pages, boolean available, List<String> tags) {
            this.isbn = isbn;
            this.title = title;
            this.pages = pages;
            this.available = available;
            this.tags = new ArrayList<>(tags);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Book)) {
                return false;
            }
            Book book = (Book) other;
            return pages == book.pages
                    && available == book.available
                    && Objects.equals(isbn, book.isbn)
                    && Objects.equals(title, book.title)
                    && Objects.equals(tags, book.tags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(isbn, title, pages, available, tags);
        }
    }
}
