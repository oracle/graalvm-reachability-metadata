/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.xsom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.xml.xsom.ForeignAttributes;
import com.sun.xml.xsom.SCD;
import com.sun.xml.xsom.XSAnnotation;
import com.sun.xml.xsom.XSAttributeDecl;
import com.sun.xml.xsom.XSAttributeUse;
import com.sun.xml.xsom.XSComplexType;
import com.sun.xml.xsom.XSComponent;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSFacet;
import com.sun.xml.xsom.XSIdentityConstraint;
import com.sun.xml.xsom.XSListSimpleType;
import com.sun.xml.xsom.XSModelGroup;
import com.sun.xml.xsom.XSModelGroupDecl;
import com.sun.xml.xsom.XSNotation;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSRestrictionSimpleType;
import com.sun.xml.xsom.XSSchema;
import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.XSSimpleType;
import com.sun.xml.xsom.XSTerm;
import com.sun.xml.xsom.XSType;
import com.sun.xml.xsom.XSUnionSimpleType;
import com.sun.xml.xsom.XSWildcard;
import com.sun.xml.xsom.XmlString;
import com.sun.xml.xsom.parser.SchemaDocument;
import com.sun.xml.xsom.parser.XSOMParser;
import com.sun.xml.xsom.util.DomAnnotationParserFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.SAXParserFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class XsomTest {
    private static final String XML_SCHEMA_NS = "http://www.w3.org/2001/XMLSchema";
    private static final String SHOP_NS = "urn:shop";
    private static final String EXTERNAL_NS = "urn:external";
    private static final String APP_NS = "urn:app";

    @Test
    void parsesSchemaAndNavigatesTypesParticlesAttributesAndIdentityConstraints() throws Exception {
        XSSchemaSet schemaSet = parseSingleSchema("memory:/shop.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:tns="urn:shop"
                           targetNamespace="urn:shop"
                           elementFormDefault="qualified">
                  <xs:simpleType name="SkuCode">
                    <xs:restriction base="xs:string">
                      <xs:pattern value="[A-Z]{3}-\\d{3}"/>
                      <xs:enumeration value="ABC-123"/>
                      <xs:enumeration value="XYZ-999"/>
                    </xs:restriction>
                  </xs:simpleType>
                  <xs:simpleType name="SkuList">
                    <xs:list itemType="tns:SkuCode"/>
                  </xs:simpleType>
                  <xs:simpleType name="CodeOrQuantity">
                    <xs:union memberTypes="tns:SkuCode xs:positiveInteger"/>
                  </xs:simpleType>
                  <xs:attribute name="currency" type="xs:string" default="USD"/>
                  <xs:complexType name="LineItemType">
                    <xs:sequence>
                      <xs:element name="sku" type="tns:SkuCode"/>
                      <xs:element name="quantity" type="xs:int" minOccurs="1" maxOccurs="unbounded"/>
                    </xs:sequence>
                    <xs:attribute ref="tns:currency" use="optional"/>
                    <xs:attribute name="status" type="tns:CodeOrQuantity" use="required" fixed="ABC-123"/>
                  </xs:complexType>
                  <xs:complexType name="OrderType">
                    <xs:sequence>
                      <xs:element name="line" type="tns:LineItemType" maxOccurs="unbounded"/>
                      <xs:choice minOccurs="0">
                        <xs:element name="note" type="xs:string"/>
                        <xs:any namespace="##other" processContents="lax"/>
                      </xs:choice>
                    </xs:sequence>
                    <xs:attribute name="priority" type="xs:boolean" default="false"/>
                  </xs:complexType>
                  <xs:element name="order" type="tns:OrderType" nillable="true">
                    <xs:key name="lineSkuKey">
                      <xs:selector xpath="tns:line"/>
                      <xs:field xpath="tns:sku"/>
                    </xs:key>
                  </xs:element>
                </xs:schema>
                """);

        XSSchema schema = schemaSet.getSchema(SHOP_NS);
        assertThat(schema.getTargetNamespace()).isEqualTo(SHOP_NS);
        assertThat(schema.getRoot()).isSameAs(schemaSet);
        assertThat(schemaSet.getSchema(XML_SCHEMA_NS)).isNotNull();

        XSSimpleType skuCode = schemaSet.getSimpleType(SHOP_NS, "SkuCode");
        assertThat(skuCode.isSimpleType()).isTrue();
        assertThat(skuCode.isRestriction()).isTrue();
        XSRestrictionSimpleType skuRestriction = skuCode.asRestriction();
        XSFacet pattern = skuRestriction.getDeclaredFacet(XSFacet.FACET_PATTERN);
        assertThat(pattern.getValue().value).isEqualTo("[A-Z]{3}-\\d{3}");
        assertThat(skuRestriction.getDeclaredFacets(XSFacet.FACET_ENUMERATION))
                .extracting(facet -> facet.getValue().value)
                .containsExactly("ABC-123", "XYZ-999");

        XSSimpleType skuListType = schema.getSimpleType("SkuList");
        assertThat(skuListType.isList()).isTrue();
        XSListSimpleType skuList = skuListType.asList();
        assertThat(skuList.getItemType().getName()).isEqualTo("SkuCode");

        XSSimpleType codeOrQuantityType = schema.getSimpleType("CodeOrQuantity");
        assertThat(codeOrQuantityType.isUnion()).isTrue();
        XSUnionSimpleType codeOrQuantity = codeOrQuantityType.asUnion();
        assertThat(codeOrQuantity.getMemberSize()).isEqualTo(2);
        assertThat(codeOrQuantity.getMember(0).getName()).isEqualTo("SkuCode");
        assertThat(codeOrQuantity.getMember(1).getTargetNamespace()).isEqualTo(XML_SCHEMA_NS);

        XSComplexType lineItemType = schemaSet.getComplexType(SHOP_NS, "LineItemType");
        assertThat(lineItemType.isComplexType()).isTrue();
        assertThat(lineItemType.getBaseType()).isSameAs(schemaSet.getAnyType());
        XSAttributeUse currency = attributeUse(lineItemType, "currency");
        assertThat(currency.isRequired()).isFalse();
        assertThat(currency.getDecl().getTargetNamespace()).isEqualTo(SHOP_NS);
        assertThat(currency.getDecl().getDefaultValue().value).isEqualTo("USD");
        XSAttributeUse status = lineItemType.getAttributeUse("", "status");
        assertThat(status.isRequired()).isTrue();
        assertThat(status.getFixedValue().value).isEqualTo("ABC-123");

        XSModelGroup lineItemChildren = lineItemType.getContentType().asParticle().getTerm().asModelGroup();
        assertThat(lineItemChildren.getCompositor()).isSameAs(XSModelGroup.SEQUENCE);
        assertThat(lineItemChildren.getSize()).isEqualTo(2);
        assertThat(lineItemChildren.getChild(0).getTerm().asElementDecl().getName()).isEqualTo("sku");
        XSParticle quantityParticle = lineItemChildren.getChild(1);
        assertThat(quantityParticle.getTerm().asElementDecl().getName()).isEqualTo("quantity");
        assertThat(quantityParticle.isRepeated()).isTrue();

        XSElementDecl order = schema.getElementDecl("order");
        assertThat(order.isGlobal()).isTrue();
        assertThat(order.isNillable()).isTrue();
        assertThat(order.getType().getName()).isEqualTo("OrderType");
        assertThat(order.getIdentityConstraints()).hasSize(1);
        XSIdentityConstraint key = order.getIdentityConstraints().get(0);
        assertThat(key.getCategory()).isEqualTo(XSIdentityConstraint.KEY);
        assertThat(key.getName()).isEqualTo("lineSkuKey");
        assertThat(key.getSelector().getXPath().value).isEqualTo("tns:line");
        assertThat(key.getFields()).extracting(field -> field.getXPath().value).containsExactly("tns:sku");

        XSComplexType orderType = order.getType().asComplexType();
        XSModelGroup orderChildren = orderType.getContentType().asParticle().getTerm().asModelGroup();
        assertThat(orderChildren.getChild(0).getTerm().asElementDecl().getName()).isEqualTo("line");
        XSTerm choiceTerm = orderChildren.getChild(1).getTerm();
        assertThat(choiceTerm.isModelGroup()).isTrue();
        XSModelGroup choice = choiceTerm.asModelGroup();
        assertThat(choice.getCompositor()).isSameAs(XSModelGroup.CHOICE);
        assertThat(choice.getChild(0).getTerm().asElementDecl().getName()).isEqualTo("note");
        XSWildcard wildcard = choice.getChild(1).getTerm().asWildcard();
        assertThat(wildcard.getMode()).isEqualTo(XSWildcard.LAX);
        assertThat(wildcard.acceptsNamespace(EXTERNAL_NS)).isTrue();
        assertThat(wildcard.acceptsNamespace(SHOP_NS)).isFalse();
    }

    @Test
    void resolvesIncludesImportsModelGroupsAttributeGroupsAndSchemaDocuments() throws Exception {
        Map<String, String> schemas = new HashMap<>();
        schemas.put("memory:/common.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:tns="urn:shop"
                           targetNamespace="urn:shop"
                           elementFormDefault="qualified">
                  <xs:attributeGroup name="auditAttributes">
                    <xs:attribute name="createdBy" type="xs:string" use="required"/>
                    <xs:attribute name="revision" type="xs:int" default="1"/>
                  </xs:attributeGroup>
                  <xs:group name="personName">
                    <xs:sequence>
                      <xs:element name="firstName" type="xs:string"/>
                      <xs:element name="lastName" type="xs:string"/>
                    </xs:sequence>
                  </xs:group>
                </xs:schema>
                """);
        schemas.put("memory:/external.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:ext="urn:external"
                           targetNamespace="urn:external"
                           elementFormDefault="qualified">
                  <xs:element name="address" type="xs:string"/>
                </xs:schema>
                """);
        schemas.put("memory:/main.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:tns="urn:shop"
                           xmlns:ext="urn:external"
                           targetNamespace="urn:shop"
                           elementFormDefault="qualified">
                  <xs:include schemaLocation="memory:/common.xsd"/>
                  <xs:import namespace="urn:external" schemaLocation="memory:/external.xsd"/>
                  <xs:complexType name="ShippingType">
                    <xs:sequence>
                      <xs:group ref="tns:personName"/>
                      <xs:element ref="ext:address" minOccurs="0"/>
                    </xs:sequence>
                    <xs:attributeGroup ref="tns:auditAttributes"/>
                    <xs:anyAttribute namespace="##other" processContents="lax"/>
                  </xs:complexType>
                  <xs:element name="shipment" type="tns:ShippingType"/>
                </xs:schema>
                """);

        XSOMParser parser = newParser();
        parser.setEntityResolver(memoryResolver(schemas));
        parser.parse(inputSource(schemas.get("memory:/main.xsd"), "memory:/main.xsd"));
        XSSchemaSet schemaSet = parser.getResult();

        Set<String> documentIds = parser.getDocuments().stream()
                .map(SchemaDocument::getSystemId)
                .collect(Collectors.toSet());
        assertThat(documentIds).contains("memory:/main.xsd", "memory:/common.xsd", "memory:/external.xsd");

        SchemaDocument mainDocument = documentWithSystemId(parser.getDocuments(), "memory:/main.xsd");
        Set<String> referencedIds = mainDocument.getReferencedDocuments().stream()
                .map(SchemaDocument::getSystemId)
                .collect(Collectors.toSet());
        assertThat(referencedIds).contains("memory:/common.xsd", "memory:/external.xsd");
        assertThat(mainDocument.getIncludedDocuments())
                .extracting(SchemaDocument::getSystemId)
                .contains("memory:/common.xsd");
        assertThat(mainDocument.getImportedDocuments(EXTERNAL_NS))
                .extracting(SchemaDocument::getSystemId)
                .contains("memory:/external.xsd");

        XSSchema shopSchema = schemaSet.getSchema(SHOP_NS);
        XSSchema externalSchema = schemaSet.getSchema(EXTERNAL_NS);
        assertThat(shopSchema.getAttGroupDecl("auditAttributes")).isNotNull();
        XSModelGroupDecl personName = shopSchema.getModelGroupDecl("personName");
        assertThat(personName.getModelGroup().getSize()).isEqualTo(2);
        assertThat(externalSchema.getElementDecl("address").getType().getTargetNamespace()).isEqualTo(XML_SCHEMA_NS);

        XSComplexType shippingType = shopSchema.getComplexType("ShippingType");
        assertThat(shippingType.getAttributeUse("", "createdBy").isRequired()).isTrue();
        assertThat(shippingType.getAttributeUse("", "revision").getDecl().getDefaultValue().value).isEqualTo("1");
        assertThat(shippingType.getAttributeWildcard().acceptsNamespace(EXTERNAL_NS)).isTrue();
        assertThat(shippingType.getAttributeWildcard().acceptsNamespace(SHOP_NS)).isFalse();

        XSModelGroup shippingChildren = shippingType.getContentType().asParticle().getTerm().asModelGroup();
        XSTerm groupReference = shippingChildren.getChild(0).getTerm();
        assertThat(groupReference.isModelGroupDecl()).isTrue();
        assertThat(groupReference.asModelGroupDecl().getName()).isEqualTo("personName");
        XSElementDecl importedAddress = shippingChildren.getChild(1).getTerm().asElementDecl();
        assertThat(importedAddress.getTargetNamespace()).isEqualTo(EXTERNAL_NS);
        assertThat(importedAddress.getName()).isEqualTo("address");
    }

    @Test
    void exposesSubstitutionGroupsAndDerivedComplexTypes() throws Exception {
        XSSchemaSet schemaSet = parseSingleSchema("memory:/animals.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:tns="urn:shop"
                           targetNamespace="urn:shop"
                           elementFormDefault="qualified">
                  <xs:complexType name="AnimalType">
                    <xs:sequence>
                      <xs:element name="name" type="xs:string"/>
                    </xs:sequence>
                  </xs:complexType>
                  <xs:complexType name="DogType">
                    <xs:complexContent>
                      <xs:extension base="tns:AnimalType">
                        <xs:sequence>
                          <xs:element name="barkVolume" type="xs:int"/>
                        </xs:sequence>
                        <xs:attribute name="breed" type="xs:string"/>
                      </xs:extension>
                    </xs:complexContent>
                  </xs:complexType>
                  <xs:complexType name="CatType">
                    <xs:complexContent>
                      <xs:extension base="tns:AnimalType">
                        <xs:sequence>
                          <xs:element name="lives" type="xs:int"/>
                        </xs:sequence>
                      </xs:extension>
                    </xs:complexContent>
                  </xs:complexType>
                  <xs:element name="animal" type="tns:AnimalType" abstract="true"/>
                  <xs:element name="dog" type="tns:DogType" substitutionGroup="tns:animal"/>
                  <xs:element name="cat" type="tns:CatType" substitutionGroup="tns:animal"/>
                </xs:schema>
                """);

        XSElementDecl animal = schemaSet.getElementDecl(SHOP_NS, "animal");
        XSElementDecl dog = schemaSet.getElementDecl(SHOP_NS, "dog");
        XSElementDecl cat = schemaSet.getElementDecl(SHOP_NS, "cat");
        assertThat(animal.isAbstract()).isTrue();
        assertThat(animal.getSubstAffiliation()).isNull();
        assertThat(dog.getSubstAffiliation()).isSameAs(animal);
        assertThat(cat.getSubstAffiliation()).isSameAs(animal);
        assertThat(animal.canBeSubstitutedBy(dog)).isTrue();
        assertThat(animal.canBeSubstitutedBy(cat)).isTrue();
        assertThat(dog.canBeSubstitutedBy(animal)).isFalse();
        assertThat(animal.listSubstitutables()).contains(animal, dog, cat);
        assertThat(animal.getSubstitutables())
                .extracting(XSElementDecl::getName)
                .contains("animal", "dog", "cat");

        XSComplexType animalType = schemaSet.getComplexType(SHOP_NS, "AnimalType");
        XSComplexType dogType = schemaSet.getComplexType(SHOP_NS, "DogType");
        XSComplexType catType = schemaSet.getComplexType(SHOP_NS, "CatType");
        assertThat(dogType.getBaseType()).isSameAs(animalType);
        assertThat(dogType.getDerivationMethod()).isEqualTo(XSType.EXTENSION);
        assertThat(dogType.isDerivedFrom(animalType)).isTrue();
        assertThat(catType.isDerivedFrom(animalType)).isTrue();
        assertThat(animalType.getSubtypes()).contains(dogType, catType);
        assertThat(dogType.getElementDecls()).contains(dog);
        assertThat(catType.getElementDecls()).contains(cat);
    }

    @Test
    void resolvesSchemaComponentDesignatorsAndNotations() throws Exception {
        XSSchemaSet schemaSet = parseSingleSchema("memory:/assets.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:tns="urn:shop"
                           targetNamespace="urn:shop"
                           elementFormDefault="qualified">
                  <xs:notation name="png" public="image/png" system="viewer:png"/>
                  <xs:complexType name="ImageType">
                    <xs:sequence>
                      <xs:element name="title" type="xs:string"/>
                      <xs:element name="caption" type="xs:string" minOccurs="0"/>
                    </xs:sequence>
                    <xs:attribute name="format" type="xs:string" use="required"/>
                  </xs:complexType>
                  <xs:element name="image" type="tns:ImageType"/>
                </xs:schema>
                """);
        NamespaceContext namespaces = namespaceContext(Map.of(
                "shop", SHOP_NS,
                "xs", XML_SCHEMA_NS));

        XSSchema schema = schemaSet.getSchema(SHOP_NS);
        XSNotation png = schema.getNotation("png");
        assertThat(png.isGlobal()).isTrue();
        assertThat(png.getPublicId()).isEqualTo("image/png");
        assertThat(png.getSystemId()).isEqualTo("viewer:png");
        assertThat(schemaSet.iterateNotations()).toIterable().contains(png);

        XSComponent imageType = schemaSet.selectSingle("x-schema::shop/type::shop:ImageType", namespaces);
        assertThat(imageType).isSameAs(schema.getComplexType("ImageType"));
        XSComponent selectedNotation = SCD.create("x-schema::shop/notation::shop:png", namespaces).selectSingle(schemaSet);
        assertThat(selectedNotation).isSameAs(png);

        XSComponent titleComponent = schemaSet.selectSingle(
                "x-schema::shop/type::shop:ImageType/model::sequence/element::shop:title", namespaces);
        assertThat(titleComponent).isInstanceOf(XSElementDecl.class);
        XSElementDecl title = (XSElementDecl) titleComponent;
        assertThat(title.isLocal()).isTrue();
        assertThat(title.getType()).isSameAs(schemaSet.getSimpleType(XML_SCHEMA_NS, "string"));

        XSComponent formatComponent = imageType.selectSingle("@format", namespaces);
        assertThat(formatComponent).isInstanceOf(XSAttributeDecl.class);
        XSAttributeDecl format = (XSAttributeDecl) formatComponent;
        assertThat(format.getName()).isEqualTo("format");
        assertThat(format.isLocal()).isTrue();
    }

    @Test
    void parsesDomAnnotationsForeignAttributesAndXmlStringValues() throws Exception {
        XSOMParser parser = newParser();
        parser.setAnnotationParser(new DomAnnotationParserFactory());
        parser.parse(new StringReader("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:app="urn:app"
                           xmlns:meta="urn:metadata"
                           targetNamespace="urn:shop"
                           elementFormDefault="qualified">
                  <xs:element name="annotated" type="xs:string" app:version="1.2">
                    <xs:annotation>
                      <xs:appinfo>
                        <meta:display-name>Annotated value</meta:display-name>
                      </xs:appinfo>
                      <xs:documentation xml:lang="en">A documented element.</xs:documentation>
                    </xs:annotation>
                  </xs:element>
                </xs:schema>
                """));
        XSSchemaSet schemaSet = parser.getResult();

        XSElementDecl annotated = schemaSet.getElementDecl(SHOP_NS, "annotated");
        assertThat(annotated.getForeignAttribute(APP_NS, "version")).isEqualTo("1.2");
        assertThat(annotated.getForeignAttributes()).hasSize(1);
        ForeignAttributes foreignAttributes = annotated.getForeignAttributes().get(0);
        assertThat(foreignAttributes.getValue(APP_NS, "version")).isEqualTo("1.2");
        assertThat(foreignAttributes.getLocator().getLineNumber()).isPositive();

        XSElementDecl annotatedAnnotationAccess = annotated;
        XSAnnotation annotation = annotatedAnnotationAccess.getAnnotation();
        assertThat(annotation.getLocator().getLineNumber()).isPositive();
        XSAnnotation xsAnnotationAccess = annotation;
        assertThat(xsAnnotationAccess.getAnnotation()).isInstanceOf(Element.class);
        Element annotationElement = (Element) xsAnnotationAccess.getAnnotation();
        assertThat(annotationElement.getLocalName()).isEqualTo("annotation");
        assertThat(annotationElement.getElementsByTagNameNS("urn:metadata", "display-name").item(0).getTextContent())
                .isEqualTo("Annotated value");
        assertThat(annotationElement.getElementsByTagNameNS(XML_SCHEMA_NS, "documentation").item(0).getTextContent())
                .contains("documented element");

        XmlString xmlString = new XmlString("literal-value");
        assertThat(xmlString.value).isEqualTo("literal-value");
        assertThat(xmlString.toString()).isEqualTo("literal-value");
        assertThat(xmlString.resolvePrefix("unused")).isNull();
    }

    @Test
    void reportsSchemaErrorsThroughConfiguredErrorHandler() throws Exception {
        XSOMParser parser = newParser();
        RecordingErrorHandler errorHandler = new RecordingErrorHandler();
        parser.setErrorHandler(errorHandler);
        parser.parse(inputSource("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:tns="urn:shop"
                           targetNamespace="urn:shop">
                  <xs:element name="broken" type="tns:MissingType"/>
                </xs:schema>
                """, "memory:/broken.xsd"));

        assertThatThrownBy(parser::getResult)
                .isInstanceOf(InternalError.class)
                .hasMessageContaining("unresolved reference");
        assertThat(errorHandler.errorCount()).isGreaterThan(0);
    }

    private static XSSchemaSet parseSingleSchema(String systemId, String schema) throws SAXException, IOException {
        XSOMParser parser = newParser();
        parser.parse(inputSource(schema, systemId));
        return parser.getResult();
    }

    private static XSOMParser newParser() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        return new XSOMParser(factory);
    }

    private static InputSource inputSource(String xml, String systemId) {
        InputSource inputSource = new InputSource(new StringReader(xml));
        inputSource.setSystemId(systemId);
        return inputSource;
    }

    private static EntityResolver memoryResolver(Map<String, String> schemas) {
        return (publicId, systemId) -> {
            String schema = schemas.get(systemId);
            if (schema == null) {
                return null;
            }
            InputSource source = inputSource(schema, systemId);
            source.setPublicId(publicId);
            return source;
        };
    }

    private static NamespaceContext namespaceContext(Map<String, String> namespaces) {
        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                if (prefix == null) {
                    throw new IllegalArgumentException("prefix");
                }
                return namespaces.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return namespaces.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(namespaceURI))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceURI) {
                String prefix = getPrefix(namespaceURI);
                if (prefix == null) {
                    return Collections.emptyIterator();
                }
                return Collections.singleton(prefix).iterator();
            }
        };
    }

    private static XSAttributeUse attributeUse(XSComplexType complexType, String name) {
        return complexType.getAttributeUses().stream()
                .filter(attribute -> name.equals(attribute.getDecl().getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing attribute use " + name));
    }

    private static SchemaDocument documentWithSystemId(Set<SchemaDocument> documents, String systemId) {
        return documents.stream()
                .filter(document -> systemId.equals(document.getSystemId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing schema document " + systemId));
    }

    private static final class RecordingErrorHandler extends DefaultHandler {
        private int errorCount;

        @Override
        public void error(SAXParseException exception) {
            errorCount++;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            errorCount++;
            throw exception;
        }

        int errorCount() {
            return errorCount;
        }
    }
}
