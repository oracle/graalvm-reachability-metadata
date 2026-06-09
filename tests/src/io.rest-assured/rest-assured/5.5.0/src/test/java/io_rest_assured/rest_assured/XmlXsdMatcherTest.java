/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import io.restassured.internal.matcher.xml.XmlXsdMatcher;
import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import static io.restassured.matcher.RestAssuredMatchers.matchesXsd;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class XmlXsdMatcherTest {
    private static final String XML = """
            <person>
                <name>Ada Lovelace</name>
            </person>
            """;

    private static final String SIMPLE_SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="person" type="personType"/>
                <xs:complexType name="personType">
                    <xs:sequence>
                        <xs:element name="name" type="xs:string"/>
                    </xs:sequence>
                </xs:complexType>
            </xs:schema>
            """;

    private static final String MAIN_SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:include schemaLocation="person-types.xsd"/>
                <xs:element name="person" type="personType"/>
            </xs:schema>
            """;

    private static final String INCLUDED_SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:complexType name="personType">
                    <xs:sequence>
                        <xs:element name="name" type="xs:string"/>
                    </xs:sequence>
                </xs:complexType>
            </xs:schema>
            """;

    @Test
    void validatesXmlDocumentAgainstXsdMatcherWithResourceResolver() {
        AtomicBoolean resolvedIncludedSchema = new AtomicBoolean();
        LSResourceResolver resolver = (type, namespaceUri, publicId, systemId, baseUri) -> {
            if (!"person-types.xsd".equals(systemId)) {
                return null;
            }
            resolvedIncludedSchema.set(true);
            return new StringLsInput(publicId, systemId, INCLUDED_SCHEMA);
        };

        assertThat(XML, matchesXsd(MAIN_SCHEMA).with(resolver));
        assertThat(resolvedIncludedSchema.get(), is(true));
    }

    @Test
    void validatesXmlDocumentAgainstXsdMatcherFromReader() {
        assertThat(XML, matchesXsd(new StringReader(SIMPLE_SCHEMA)));
    }

    @Test
    void validatesXmlDocumentAgainstXsdMatcherFromFile(@TempDir Path tempDir) throws IOException {
        Path schema = tempDir.resolve("person.xsd");
        Files.writeString(schema, SIMPLE_SCHEMA, StandardCharsets.UTF_8);

        assertThat(XML, matchesXsd(schema.toFile()));
    }

    @Test
    void describesXsdExpectation() {
        StringDescription description = new StringDescription();

        matchesXsd(SIMPLE_SCHEMA).describeTo(description);

        assertThat(description.toString(), is("the supplied XSD"));
    }

    @Test
    void resolvesRuntimeSelectedTypeThroughGeneratedGroovyClassLookup() throws Exception {
        MethodHandle classLookup = MethodHandles.privateLookupIn(XmlXsdMatcher.class, MethodHandles.lookup())
                .findStatic(XmlXsdMatcher.class, "class$", MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = invokeClassLookup(classLookup, runtimeSelectedRestAssuredTypeName());

        assertThat(resolvedClass.getName(), is("io.restassured.http.ContentType"));
    }

    private static Class<?> invokeClassLookup(MethodHandle classLookup, String className) throws Exception {
        try {
            return (Class<?>) classLookup.invoke(className);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    private static String runtimeSelectedRestAssuredTypeName() {
        String packageName = String.join(".", "io", "restassured", "http");
        String simpleName = new StringBuilder("Content").append("Type").toString();
        return packageName + "." + simpleName;
    }

    private static final class StringLsInput implements LSInput {
        private Reader characterStream;
        private InputStream byteStream;
        private String publicId;
        private String systemId;
        private String baseUri;
        private String stringData;
        private String encoding;
        private boolean certifiedText;

        private StringLsInput(String publicId, String systemId, String stringData) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.stringData = stringData;
        }

        @Override
        public Reader getCharacterStream() {
            return characterStream;
        }

        @Override
        public void setCharacterStream(Reader characterStream) {
            this.characterStream = characterStream;
        }

        @Override
        public InputStream getByteStream() {
            return byteStream;
        }

        @Override
        public void setByteStream(InputStream byteStream) {
            this.byteStream = byteStream;
        }

        @Override
        public String getStringData() {
            return stringData;
        }

        @Override
        public void setStringData(String stringData) {
            this.stringData = stringData;
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public void setSystemId(String systemId) {
            this.systemId = systemId;
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public void setPublicId(String publicId) {
            this.publicId = publicId;
        }

        @Override
        public String getBaseURI() {
            return baseUri;
        }

        @Override
        public void setBaseURI(String baseUri) {
            this.baseUri = baseUri;
        }

        @Override
        public String getEncoding() {
            return encoding;
        }

        @Override
        public void setEncoding(String encoding) {
            this.encoding = encoding;
        }

        @Override
        public boolean getCertifiedText() {
            return certifiedText;
        }

        @Override
        public void setCertifiedText(boolean certifiedText) {
            this.certifiedText = certifiedText;
        }
    }
}
