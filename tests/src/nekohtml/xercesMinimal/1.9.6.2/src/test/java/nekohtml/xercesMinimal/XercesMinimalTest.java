/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package nekohtml.xercesMinimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.apache.xerces.util.AugmentationsImpl;
import org.apache.xerces.util.DefaultErrorHandler;
import org.apache.xerces.util.EncodingMap;
import org.apache.xerces.util.NamespaceSupport;
import org.apache.xerces.util.ParserConfigurationSettings;
import org.apache.xerces.util.URI;
import org.apache.xerces.util.XMLAttributesImpl;
import org.apache.xerces.util.XMLResourceIdentifierImpl;
import org.apache.xerces.util.XMLStringBuffer;
import org.apache.xerces.util.XMLSymbols;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.NamespaceContext;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XMLString;
import org.apache.xerces.xni.parser.XMLComponentManager;
import org.apache.xerces.xni.parser.XMLConfigurationException;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xni.parser.XMLParseException;
import org.junit.jupiter.api.Test;

public class XercesMinimalTest {
    @Test
    void xmlStringAndBufferPreserveOffsetsAndAppendDifferentSources() {
        char[] source = "0123456789".toCharArray();
        XMLString slice = new XMLString(source, 2, 4);

        assertThat(slice.toString()).isEqualTo("2345");
        assertThat(slice.equals("2345")).isTrue();
        assertThat(slice.equals("x2345x".toCharArray(), 1, 4)).isTrue();

        XMLStringBuffer buffer = new XMLStringBuffer('[');
        buffer.append(slice);
        buffer.append(']');
        buffer.append(" tail");
        buffer.append(" abc ".toCharArray(), 1, 3);

        assertThat(buffer.toString()).isEqualTo("[2345] tailabc");

        buffer.clear();
        assertThat(buffer.length).isZero();
        assertThat(buffer.toString()).isEmpty();
    }

    @Test
    void qNameCopiesClonesClearsAndComparesByXmlNameParts() {
        QName name = new QName("html", "body", "html:body", "http://www.w3.org/1999/xhtml");
        QName copy = new QName(name);
        QName clone = (QName) name.clone();

        assertThat(copy).isEqualTo(name);
        assertThat(clone).isEqualTo(name);
        assertThat(copy.hashCode()).isEqualTo(name.hashCode());
        assertThat(name.toString()).contains("html", "body", "http://www.w3.org/1999/xhtml");

        copy.setValues("svg", "path", "svg:path", "urn:svg");
        assertThat(copy).isNotEqualTo(name);

        copy.clear();
        assertThat(copy.prefix).isNull();
        assertThat(copy.localpart).isNull();
        assertThat(copy.rawname).isNull();
        assertThat(copy.uri).isNull();
    }

    @Test
    void attributesSupportNamespacedLookupMutationAugmentationsAndRemoval() {
        XMLAttributesImpl attributes = new XMLAttributesImpl();
        attributes.setNamespaces(true);
        QName name = new QName("p", "class", "p:class", "urn:test");

        int index = attributes.addAttribute(name, XMLSymbols.fCDATASymbol, "hero");
        assertThat(index).isZero();
        assertThat(attributes.getLength()).isEqualTo(1);
        assertThat(attributes.getIndex("p:class")).isEqualTo(index);
        assertThat(attributes.getIndex("urn:test", "class")).isEqualTo(index);
        assertThat(attributes.getQName(index)).isEqualTo("p:class");
        assertThat(attributes.getPrefix(index)).isEqualTo("p");
        assertThat(attributes.getLocalName(index)).isEqualTo("class");
        assertThat(attributes.getURI(index)).isEqualTo("urn:test");
        assertThat(attributes.getType(index)).isEqualTo(XMLSymbols.fCDATASymbol);
        assertThat(attributes.getValue(index)).isEqualTo("hero");

        attributes.setType(index, XMLSymbols.fIDSymbol);
        attributes.setValue(index, "main");
        attributes.setNonNormalizedValue(index, " main ");
        attributes.setSpecified(index, false);
        attributes.setSchemaId(index, true);
        AugmentationsImpl augmentations = new AugmentationsImpl();
        augmentations.putItem("source", "literal");
        attributes.setAugmentations(index, augmentations);

        assertThat(attributes.getType("p:class")).isEqualTo(XMLSymbols.fIDSymbol);
        assertThat(attributes.getValue("urn:test", "class")).isEqualTo("main");
        assertThat(attributes.getNonNormalizedValue(index)).isEqualTo(" main ");
        assertThat(attributes.isSpecified(index)).isFalse();
        assertThat(attributes.getSchemaId("urn:test", "class")).isTrue();
        assertThat(attributes.getAugmentations("p:class").getItem("source")).isEqualTo("literal");

        QName copiedName = new QName();
        attributes.getName(index, copiedName);
        assertThat(copiedName).isEqualTo(name);

        attributes.addAttribute(new QName(null, "title", "title", null), XMLSymbols.fCDATASymbol, "Welcome");
        attributes.removeAttributeAt(index);
        assertThat(attributes.getLength()).isEqualTo(1);
        assertThat(attributes.getQName(0)).isEqualTo("title");

        attributes.removeAllAttributes();
        assertThat(attributes.getLength()).isZero();
    }

    @Test
    void attributesDetectDuplicateExpandedNames() {
        XMLAttributesImpl attributes = new XMLAttributesImpl();
        attributes.setNamespaces(true);
        attributes.addAttributeNS(new QName("a", "id", "a:id", "urn:duplicate"), XMLSymbols.fIDSymbol, "first");
        attributes.addAttributeNS(new QName("b", "id", "b:id", "urn:duplicate"), XMLSymbols.fIDSymbol, "second");

        QName duplicate = attributes.checkDuplicatesNS();

        assertThat(duplicate).isNotNull();
        assertThat(duplicate.localpart).isEqualTo("id");
        assertThat(duplicate.uri).isEqualTo("urn:duplicate");
    }

    @Test
    void augmentationsGrowBeyondSmallContainerAndExposeMutableItems() {
        AugmentationsImpl augmentations = new AugmentationsImpl();

        for (int i = 0; i < 12; i++) {
            assertThat(augmentations.putItem("key-" + i, "value-" + i)).isNull();
        }

        assertThat(augmentations.putItem("key-3", "replacement")).isEqualTo("value-3");
        assertThat(augmentations.getItem("key-3")).isEqualTo("replacement");
        assertThat(keys(augmentations)).contains("key-0", "key-3", "key-11");

        assertThat(augmentations.removeItem("key-3")).isEqualTo("replacement");
        assertThat(augmentations.getItem("key-3")).isNull();

        augmentations.removeAllItems();
        assertThat(keys(augmentations)).isEmpty();
    }

    @Test
    void namespaceSupportScopesPrefixDeclarationsByContext() {
        NamespaceSupport namespaces = new NamespaceSupport();
        namespaces.reset();

        assertThat(namespaces.getURI("xml")).isEqualTo(NamespaceContext.XML_URI);
        assertThat(namespaces.declarePrefix("xml", "urn:cannot-rebind")).isFalse();
        assertThat(namespaces.declarePrefix("xmlns", "urn:cannot-rebind")).isFalse();

        assertThat(namespaces.declarePrefix("", "urn:default")).isTrue();
        assertThat(namespaces.declarePrefix("p", "urn:outer")).isTrue();
        assertThat(namespaces.getURI("")).isEqualTo("urn:default");
        assertThat(namespaces.getURI("p")).isEqualTo("urn:outer");
        assertThat(namespaces.getPrefix("urn:outer")).isEqualTo("p");
        assertThat(namespaces.containsPrefix("p")).isTrue();
        assertThat(allPrefixes(namespaces)).contains("p");

        namespaces.pushContext();
        assertThat(namespaces.declarePrefix("p", "urn:inner")).isTrue();
        assertThat(namespaces.declarePrefix("q", "urn:secondary")).isTrue();
        assertThat(namespaces.getURI("p")).isEqualTo("urn:inner");
        assertThat(namespaces.getDeclaredPrefixCount()).isEqualTo(2);
        assertThat(Arrays.asList(namespaces.getDeclaredPrefixAt(0), namespaces.getDeclaredPrefixAt(1)))
                .containsExactlyInAnyOrder("p", "q");

        namespaces.popContext();
        assertThat(namespaces.getURI("p")).isEqualTo("urn:outer");
        assertThat(namespaces.getURI("q")).isNull();
    }

    @Test
    void uriParsesResolvesMutatesAndValidatesIdentifiers() throws Exception {
        URI uri = new URI("http://user@example.com:8080/a/b.xml?x=1#top");

        assertThat(uri.getScheme()).isEqualTo("http");
        assertThat(uri.getUserinfo()).isEqualTo("user");
        assertThat(uri.getHost()).isEqualTo("example.com");
        assertThat(uri.getPort()).isEqualTo(8080);
        assertThat(uri.getPath()).isEqualTo("/a/b.xml");
        assertThat(uri.getQueryString()).isEqualTo("x=1");
        assertThat(uri.getFragment()).isEqualTo("top");
        assertThat(uri.isGenericURI()).isTrue();
        assertThat(uri.isAbsoluteURI()).isTrue();

        URI resolved = new URI(new URI("http://example.com/root/branch/file.xml"), "../leaf.xml?ok=true#frag");
        assertThat(resolved.toString()).isEqualTo("http://example.com/root/leaf.xml?ok=true#frag");

        resolved.setFragment(null);
        resolved.setQueryString(null);
        resolved.appendPath("child.xml");
        assertThat(resolved.toString()).isEqualTo("http://example.com/root/leaf.xml/child.xml");

        assertThat(URI.isConformantSchemeName("data+test.1")).isTrue();
        assertThat(URI.isConformantSchemeName("1-invalid")).isFalse();
        assertThat(URI.isWellFormedIPv4Address("192.168.0.1")).isTrue();
        assertThat(URI.isWellFormedIPv4Address("300.168.0.1")).isFalse();
    }

    @Test
    void uriSupportsOpaqueSchemeSpecificIdentifiers() throws Exception {
        URI mailbox = new URI("mailto", "person@example.org");
        URI copy = new URI(mailbox);

        assertThat(mailbox.getScheme()).isEqualTo("mailto");
        assertThat(mailbox.getSchemeSpecificPart()).isEqualTo("person@example.org");
        assertThat(mailbox.isAbsoluteURI()).isTrue();
        assertThat(mailbox.isGenericURI()).isFalse();
        assertThat(mailbox.getAuthority()).isEmpty();
        assertThat(mailbox.getHost()).isNull();
        assertThat(mailbox.getUserinfo()).isNull();
        assertThat(mailbox.getQueryString()).isNull();
        assertThat(mailbox.getFragment()).isNull();
        assertThat(copy).isEqualTo(mailbox);
        assertThat(mailbox.toString()).isEqualTo("mailto:person@example.org");
    }

    @Test
    void resourceIdentifiersCanBeCopiedIntoInputSourcesAndCleared() throws Exception {
        XMLResourceIdentifierImpl resourceIdentifier = new XMLResourceIdentifierImpl(
                "public-id",
                "literal-system-id",
                "base-system-id",
                "expanded-system-id",
                "urn:namespace");

        assertThat(resourceIdentifier.getPublicId()).isEqualTo("public-id");
        assertThat(resourceIdentifier.getLiteralSystemId()).isEqualTo("literal-system-id");
        assertThat(resourceIdentifier.getBaseSystemId()).isEqualTo("base-system-id");
        assertThat(resourceIdentifier.getExpandedSystemId()).isEqualTo("expanded-system-id");
        assertThat(resourceIdentifier.getNamespace()).isEqualTo("urn:namespace");
        assertThat(resourceIdentifier.toString()).contains("public-id", "literal-system-id", "expanded-system-id");

        XMLInputSource identifierSource = new XMLInputSource(resourceIdentifier);
        assertThat(identifierSource.getPublicId()).isEqualTo("public-id");
        assertThat(identifierSource.getSystemId()).isEqualTo("literal-system-id");
        assertThat(identifierSource.getBaseSystemId()).isEqualTo("base-system-id");

        ByteArrayInputStream bytes = new ByteArrayInputStream("<root/>".getBytes(StandardCharsets.UTF_8));
        XMLInputSource byteSource = new XMLInputSource("public", "system", "base", bytes, "UTF-8");
        assertThat(byteSource.getByteStream().read()).isEqualTo('<');
        assertThat(byteSource.getEncoding()).isEqualTo("UTF-8");

        StringReader reader = new StringReader("<root/>");
        XMLInputSource characterSource = new XMLInputSource("public", "system", "base", reader, "UTF-16");
        assertThat(characterSource.getCharacterStream().read()).isEqualTo('<');
        assertThat(characterSource.getEncoding()).isEqualTo("UTF-16");

        resourceIdentifier.clear();
        assertThat(resourceIdentifier.getPublicId()).isNull();
        assertThat(resourceIdentifier.getLiteralSystemId()).isNull();
        assertThat(resourceIdentifier.getBaseSystemId()).isNull();
        assertThat(resourceIdentifier.getExpandedSystemId()).isNull();
        assertThat(resourceIdentifier.getNamespace()).isNull();
    }

    @Test
    void parserConfigurationStoresRecognizedFeaturesAndProperties() throws Exception {
        ParserConfigurationSettings settings = new ParserConfigurationSettings();
        String feature = "http://example.test/features/collect-comments";
        String property = "http://example.test/properties/application-state";

        settings.addRecognizedFeatures(new String[] {feature});
        settings.addRecognizedProperties(new String[] {property});
        settings.setFeature(feature, true);
        List<String> propertyValue = Arrays.asList("ready");
        settings.setProperty(property, propertyValue);

        assertThat(settings.getFeature(feature)).isTrue();
        assertThat(settings.getProperty(property)).isEqualTo(propertyValue);

        assertThatExceptionOfType(XMLConfigurationException.class)
                .isThrownBy(() -> settings.getFeature("http://example.test/features/unknown"))
                .satisfies(exception -> {
                    assertThat(exception.getType()).isEqualTo(XMLConfigurationException.NOT_RECOGNIZED);
                    assertThat(exception.getIdentifier()).isEqualTo("http://example.test/features/unknown");
                });
        assertThatExceptionOfType(XMLConfigurationException.class)
                .isThrownBy(() -> settings.setProperty("http://example.test/properties/unknown", "value"))
                .satisfies(exception -> {
                    assertThat(exception.getType()).isEqualTo(XMLConfigurationException.NOT_RECOGNIZED);
                    assertThat(exception.getIdentifier()).isEqualTo("http://example.test/properties/unknown");
                });
    }

    @Test
    void parserConfigurationDelegatesRecognitionToParentManager() throws Exception {
        String parentFeature = "http://example.test/features/parent-controlled";
        String parentProperty = "http://example.test/properties/parent-controlled";
        ParserConfigurationSettings settings = new ParserConfigurationSettings(new TestComponentManager(
                Map.of(parentFeature, false),
                Map.of(parentProperty, "parent-default")));
        Object propertyValue = List.of("child", "override");

        settings.setFeature(parentFeature, true);
        settings.setProperty(parentProperty, propertyValue);

        assertThat(settings.getFeature(parentFeature)).isTrue();
        assertThat(settings.getProperty(parentProperty)).isEqualTo(propertyValue);
    }

    @Test
    void encodingMapSupportsCustomBidirectionalMappings() {
        String ianaName = "X-NEKOHTML-TEST-IANA";
        String javaName = "X_NekoHTML_Test_Java";

        try {
            EncodingMap.putIANA2JavaMapping(ianaName, javaName);
            EncodingMap.putJava2IANAMapping(javaName, ianaName);

            assertThat(EncodingMap.getIANA2JavaMapping(ianaName)).isEqualTo(javaName);
            assertThat(EncodingMap.getJava2IANAMapping(javaName)).isEqualTo(ianaName);
            assertThat(EncodingMap.removeIANA2JavaMapping(ianaName)).isEqualTo(javaName);
            assertThat(EncodingMap.removeJava2IANAMapping(javaName)).isEqualTo(ianaName);
            assertThat(EncodingMap.getIANA2JavaMapping(ianaName)).isNull();
            assertThat(EncodingMap.getJava2IANAMapping(javaName)).isNull();
        } finally {
            EncodingMap.removeIANA2JavaMapping(ianaName);
            EncodingMap.removeJava2IANAMapping(javaName);
        }
    }

    @Test
    void parseExceptionExposesLocatorStateAndOriginalCause() {
        RuntimeException cause = new RuntimeException("root cause");
        XMLParseException exception = new XMLParseException(new TestLocator(), "Broken markup", cause);

        assertThat(exception.getMessage()).isEqualTo("Broken markup");
        assertThat(exception.getException()).isSameAs(cause);
        assertThat(exception.getPublicId()).isEqualTo("public-id");
        assertThat(exception.getLiteralSystemId()).isEqualTo("literal-system-id");
        assertThat(exception.getBaseSystemId()).isEqualTo("base-system-id");
        assertThat(exception.getExpandedSystemId()).isEqualTo("expanded-system-id");
        assertThat(exception.getLineNumber()).isEqualTo(17);
        assertThat(exception.getColumnNumber()).isEqualTo(5);
        assertThat(exception.getCharacterOffset()).isEqualTo(123);
        assertThat(exception.toString()).contains("public-id", "expanded-system-id", "17", "5", "Broken markup");
    }

    @Test
    void defaultErrorHandlerReportsRecoverableErrorsAndRethrowsFatalErrors() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
        DefaultErrorHandler handler = new DefaultErrorHandler(writer);
        XMLParseException exception = new XMLParseException(new TestLocator(), "Broken markup");

        handler.warning("domain", "warning-key", exception);
        handler.error("domain", "error-key", exception);
        assertThatExceptionOfType(XMLParseException.class)
                .isThrownBy(() -> handler.fatalError("domain", "fatal-key", exception))
                .satisfies(thrown -> assertThat(thrown).isSameAs(exception));

        writer.flush();
        assertThat(new String(output.toByteArray(), StandardCharsets.UTF_8))
                .contains("Warning", "Error", "Fatal Error", "Broken markup");
    }

    private static List<String> keys(Augmentations augmentations) {
        List<String> names = new ArrayList<>();
        Enumeration<?> keys = augmentations.keys();
        while (keys.hasMoreElements()) {
            names.add((String) keys.nextElement());
        }
        return names;
    }

    private static List<String> allPrefixes(NamespaceSupport namespaces) {
        List<String> prefixes = new ArrayList<>();
        Enumeration<?> allPrefixes = namespaces.getAllPrefixes();
        while (allPrefixes.hasMoreElements()) {
            prefixes.add((String) allPrefixes.nextElement());
        }
        return prefixes;
    }

    private static final class TestComponentManager implements XMLComponentManager {
        private final Map<String, Boolean> features;
        private final Map<String, Object> properties;

        private TestComponentManager(Map<String, Boolean> features, Map<String, Object> properties) {
            this.features = features;
            this.properties = properties;
        }

        @Override
        public boolean getFeature(String featureId) throws XMLConfigurationException {
            Boolean featureValue = features.get(featureId);
            if (featureValue == null) {
                throw new XMLConfigurationException(XMLConfigurationException.NOT_RECOGNIZED, featureId);
            }
            return featureValue;
        }

        @Override
        public Object getProperty(String propertyId) throws XMLConfigurationException {
            Object propertyValue = properties.get(propertyId);
            if (propertyValue == null) {
                throw new XMLConfigurationException(XMLConfigurationException.NOT_RECOGNIZED, propertyId);
            }
            return propertyValue;
        }
    }

    private static final class TestLocator implements XMLLocator {
        @Override
        public String getPublicId() {
            return "public-id";
        }

        @Override
        public String getLiteralSystemId() {
            return "literal-system-id";
        }

        @Override
        public String getBaseSystemId() {
            return "base-system-id";
        }

        @Override
        public String getExpandedSystemId() {
            return "expanded-system-id";
        }

        @Override
        public int getLineNumber() {
            return 17;
        }

        @Override
        public int getColumnNumber() {
            return 5;
        }

        @Override
        public int getCharacterOffset() {
            return 123;
        }

        @Override
        public String getEncoding() {
            return "UTF-8";
        }

        @Override
        public String getXMLVersion() {
            return "1.0";
        }
    }
}
