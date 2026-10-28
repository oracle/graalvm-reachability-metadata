/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_databinding_jaxb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.util.StreamReaderDelegate;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.apache.cxf.bus.extension.ExtensionManagerBus;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.apache.cxf.jaxb.JAXBEncoderDecoder;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.factory.ReflectionServiceFactoryBean;
import org.apache.cxf.service.model.InterfaceInfo;
import org.apache.cxf.service.model.MessageInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.service.model.UnwrappedOperationInfo;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaSimpleType;
import org.apache.ws.commons.schema.XmlSchemaSimpleTypeList;
import org.junit.jupiter.api.Test;

public class JAXBEncoderDecoderTest {
    private static final String NAMESPACE = "urn:cxf-jaxb-test";

    @Test
    void marshalsAndUnmarshalsCustomExceptionProperties() throws Exception {
        JaxbTestSupport.disableOptimizedAccessors();
        JAXBContext context = JAXBContext.newInstance(String.class);
        MessagePartInfo part = newPart(PropertyException.class, "fault");
        PropertyException original = new PropertyException();
        original.setDetail("invalid request");
        original.setTags(new String[] {"client", "validation"});

        StringWriter output = new StringWriter();
        XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
        outputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);
        XMLStreamWriter writer = outputFactory.createXMLStreamWriter(output);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        JAXBEncoderDecoder.marshallException(marshaller, original, part, writer);
        writer.close();

        XMLStreamReader reader = XMLInputFactory.newFactory()
                .createXMLStreamReader(new StringReader(output.toString()));
        reader.nextTag();
        PropertyException restored = (PropertyException) JAXBEncoderDecoder.unmarshallException(
                context.createUnmarshaller(), reader, part);

        assertThat(restored.getDetail()).isEqualTo("invalid request");
        assertThat(restored.getTags()).containsExactly("client", "validation");
    }

    @Test
    void marshalsFieldsAndRestoresInheritedArrays() throws Exception {
        JaxbTestSupport.disableOptimizedAccessors();
        JAXBContext context = JAXBContext.newInstance(String.class);
        FieldException original = new FieldException();
        original.detail = "field detail";
        original.labels = new String[] {"first", "second"};
        MessagePartInfo fieldPart = newPart(FieldException.class, "fieldFault");

        StringWriter output = new StringWriter();
        XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
        outputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);
        XMLStreamWriter writer = outputFactory.createXMLStreamWriter(output);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        JAXBEncoderDecoder.marshallException(marshaller, original, fieldPart, writer);
        writer.close();

        assertThat(output.toString()).contains("field detail", "first", "second");

        MessagePartInfo inheritedPart = newPart(InheritedFieldException.class, "inheritedFault");
        String xml = "<inheritedFault xmlns=\"" + NAMESPACE
                + "\"><codes>7</codes><codes>9</codes></inheritedFault>";
        XMLStreamReader reader = XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(xml));
        reader.nextTag();
        InheritedFieldException restored = (InheritedFieldException)
                JAXBEncoderDecoder.unmarshallException(context.createUnmarshaller(), reader, inheritedPart);

        assertThat(restored.codes).containsExactly(7, 9);
    }

    @Test
    void usesStringConstructorWhenRestoringException() throws Exception {
        JaxbTestSupport.disableOptimizedAccessors();
        JAXBContext context = JAXBContext.newInstance(String.class);
        MessagePartInfo part = newPart(StringConstructorException.class, "stringFault");
        String xml = "<stringFault xmlns=\"" + NAMESPACE + "\"><code>42</code></stringFault>";
        XMLStreamReader reader = XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(xml));
        reader.nextTag();

        StringConstructorException restored = (StringConstructorException)
                JAXBEncoderDecoder.unmarshallException(context.createUnmarshaller(), reader, part);

        assertThat(restored.getCode()).isEqualTo("42");
    }

    @Test
    void restoresFieldAndPropertyArraysForCustomExceptions() throws Exception {
        JaxbTestSupport.disableOptimizedAccessors();
        JAXBContext context = JAXBContext.newInstance(String.class);
        MessagePartInfo fieldPart = newPart(FieldException.class, "fieldFault");
        String fieldXml = "<fieldFault xmlns=\"" + NAMESPACE
                + "\"><detail>field detail</detail><labels>one</labels><labels>two</labels>"
                + "</fieldFault>";
        XMLStreamReader fieldReader = XMLInputFactory.newFactory()
                .createXMLStreamReader(new StringReader(fieldXml));
        fieldReader.nextTag();

        FieldException fieldException = (FieldException) JAXBEncoderDecoder.unmarshallException(
                context.createUnmarshaller(), fieldReader, fieldPart);

        assertThat(fieldException.detail).isEqualTo("field detail");
        assertThat(fieldException.labels).containsExactly("one", "two");

        MessagePartInfo propertyPart = newPart(BooleanPropertyException.class, "propertyFault");
        String propertyXml = "<propertyFault xmlns=\"" + NAMESPACE
                + "\"><retryable>true</retryable><codes>3</codes><codes>5</codes>"
                + "</propertyFault>";
        XMLStreamReader propertyReader = XMLInputFactory.newFactory()
                .createXMLStreamReader(new StringReader(propertyXml));
        propertyReader.nextTag();

        BooleanPropertyException propertyException = (BooleanPropertyException)
                JAXBEncoderDecoder.unmarshallException(
                        context.createUnmarshaller(), propertyReader, propertyPart);

        assertThat(propertyException.isRetryable()).isTrue();
        assertThat(propertyException.getCodes()).containsExactly(3, 5);
    }

    @Test
    void unmarshalsRepeatedObjectAndPrimitiveElementsAsArrays() throws Exception {
        JaxbTestSupport.disableOptimizedAccessors();
        JAXBContext context = JAXBContext.newInstance(String.class, Integer.class);

        MessagePartInfo stringPart = repeatedPart(String.class, "name");
        XMLStreamReader stringReader = repeatedReader("name", "Ada", "Grace");
        String[] names = (String[]) JAXBEncoderDecoder.unmarshall(
                context.createUnmarshaller(), stringReader, stringPart, true);

        MessagePartInfo intPart = repeatedPart(Integer.TYPE, "number");
        XMLStreamReader intReader = repeatedReader("number", "7", "11");
        int[] numbers = (int[]) JAXBEncoderDecoder.unmarshall(
                context.createUnmarshaller(), intReader, intPart, true);

        assertThat(names).containsExactly("Ada", "Grace");
        assertThat(numbers).containsExactly(7, 11);
    }

    @Test
    void preservesConcreteListAndSetTypesWhenReadingRepeatedElements() throws Exception {
        JaxbTestSupport.disableOptimizedAccessors();
        ExtensionManagerBus bus = new ExtensionManagerBus();
        try {
            JAXBDataBinding binding = new JAXBDataBinding();
            ReflectionServiceFactoryBean factory = new ReflectionServiceFactoryBean();
            factory.setBus(bus);
            factory.setServiceClass(CollectionService.class);
            factory.setWrapped(false);
            factory.setDataBinding(binding);
            Service service = factory.create();

            MessagePartInfo listPart = outputPart(service, "listValues");
            Object list = readRepeated(binding, listPart, "alpha", "beta");
            MessagePartInfo setPart = outputPart(service, "setValues");
            Object set = readRepeated(binding, setPart, "alpha", "beta");

            assertThat(list).isInstanceOf(ValueList.class);
            assertThat((ValueList<String>) list).containsExactly("alpha", "beta");
            assertThat(set).isInstanceOf(ValueSet.class);
            assertThat((ValueSet<String>) set).containsExactly("alpha", "beta");
        } finally {
            bus.shutdown(true);
        }
    }

    @Test
    void convertsJaxbSimpleListValuesToArrays() throws Exception {
        JaxbTestSupport.disableOptimizedAccessors();
        MessagePartInfo part = repeatedPart(String.class, "tokens");
        XmlSchema listSchema = new XmlSchema(NAMESPACE, new XmlSchemaCollection());
        XmlSchemaSimpleType simpleType = new XmlSchemaSimpleType(listSchema, false);
        simpleType.setContent(new XmlSchemaSimpleTypeList());
        ((XmlSchemaElement) part.getXmlSchema()).setSchemaType(simpleType);
        XMLStreamReader reader = XMLInputFactory.newFactory().createXMLStreamReader(
                new StringReader("<tokens xmlns=\"" + NAMESPACE + "\">alpha beta</tokens>"));
        reader.nextTag();
        JAXBContext context = JAXBContext.newInstance(TokenObjectFactory.class);

        String[] tokens = (String[]) JAXBEncoderDecoder.unmarshall(
                context.createUnmarshaller(), reader, part, true);

        assertThat(tokens).containsExactly("alpha", "beta");
    }

    @Test
    void resolvesInheritedNamespacesDuringSchemaValidation() throws Exception {
        JaxbTestSupport.disableOptimizedAccessors();
        String schemaText = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="urn:cxf-jaxb-test"
                           elementFormDefault="qualified">
                  <xs:element name="value" type="xs:QName"/>
                </xs:schema>
                """;
        Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(schemaText)));
        Unmarshaller unmarshaller = JAXBContext.newInstance(QName.class).createUnmarshaller();
        unmarshaller.setSchema(schema);
        XMLStreamReader delegate = XMLInputFactory.newFactory().createXMLStreamReader(
                new StringReader("<wrapper xmlns:t=\"urn:cxf-types\"><value xmlns=\""
                        + NAMESPACE + "\">t:item</value></wrapper>"));
        delegate.nextTag();
        delegate.nextTag();
        XMLStreamReader reader = new NamespaceAwareReader(delegate);

        Object value = JAXBEncoderDecoder.unmarshall(
                unmarshaller, reader, new QName(NAMESPACE, "value"), QName.class, true);

        assertThat(value).isEqualTo(new QName("urn:cxf-types", "item"));
    }

    @Test
    void resolvesGenericArrayTypes() {
        Class<?> arrayClass = JAXBEncoderDecoder.getClassFromType(new StringGenericArrayType());

        assertThat(arrayClass).isEqualTo(String[].class);
    }

    @Test
    void unmarshalsWithSchemaValidation() throws Exception {
        JaxbTestSupport.disableOptimizedAccessors();
        String schemaText = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="urn:cxf-jaxb-test"
                           elementFormDefault="qualified">
                  <xs:element name="value" type="xs:string"/>
                </xs:schema>
                """;
        Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(schemaText)));
        Unmarshaller unmarshaller = JAXBContext.newInstance(String.class).createUnmarshaller();
        unmarshaller.setSchema(schema);
        XMLStreamReader reader = XMLInputFactory.newFactory().createXMLStreamReader(
                new StringReader("<value xmlns=\"" + NAMESPACE + "\">validated</value>"));
        reader.nextTag();

        Object value = JAXBEncoderDecoder.unmarshall(
                unmarshaller, reader, new QName(NAMESPACE, "value"), String.class, true);

        assertThat(value).isEqualTo("validated");
    }

    private static MessagePartInfo outputPart(Service service, String operationName) {
        for (OperationInfo operation : service.getServiceInfos().get(0).getInterface().getOperations()) {
            if (operationName.equals(operation.getName().getLocalPart())) {
                return operation.getOutput().getMessagePart(0);
            }
        }
        throw new IllegalArgumentException("No operation named " + operationName);
    }

    private static Object readRepeated(
            JAXBDataBinding binding, MessagePartInfo servicePart, String first, String second)
            throws Exception {
        MessagePartInfo repeatedPart = repeatedPart(
                String.class, servicePart.getConcreteName().getLocalPart());
        repeatedPart.setProperty("generic.type", servicePart.getProperty("generic.type"));
        XMLStreamReader reader = repeatedReader(
                repeatedPart.getConcreteName(), first, second);
        return JAXBEncoderDecoder.unmarshall(
                binding.getContext().createUnmarshaller(), reader, repeatedPart, true);
    }

    private static MessagePartInfo repeatedPart(Class<?> componentType, String localName) {
        ServiceInfo service = new ServiceInfo();
        service.setName(new QName(NAMESPACE, "ArrayService"));
        InterfaceInfo iface = service.createInterface(new QName(NAMESPACE, "ArrayPort"));
        OperationInfo wrapped = iface.addOperation(new QName(NAMESPACE, "read" + localName));
        UnwrappedOperationInfo operation = new UnwrappedOperationInfo(wrapped);
        wrapped.setUnwrappedOperation(operation);
        MessageInfo message = operation.createMessage(
                new QName(NAMESPACE, localName + "Message"), MessageInfo.Type.OUTPUT);
        operation.setOutput(localName + "Message", message);
        MessagePartInfo part = message.addMessagePart(new QName(NAMESPACE, localName));
        part.setConcreteName(new QName(NAMESPACE, localName));
        part.setTypeClass(componentType == Integer.TYPE ? int[].class : String[].class);
        XmlSchema schema = new XmlSchema(NAMESPACE, new XmlSchemaCollection());
        XmlSchemaElement element = new XmlSchemaElement(schema, false);
        element.setName(localName);
        element.setMaxOccurs(2);
        part.setXmlSchema(element);
        return part;
    }

    private static XMLStreamReader repeatedReader(String localName, String first, String second)
            throws Exception {
        return repeatedReader(new QName(NAMESPACE, localName), first, second);
    }

    private static XMLStreamReader repeatedReader(QName name, String first, String second)
            throws Exception {
        String localName = name.getLocalPart();
        String xml = "<values xmlns=\"" + name.getNamespaceURI() + "\"><" + localName + ">" + first
                + "</" + localName + "><" + localName + ">" + second + "</" + localName
                + "></values>";
        XMLStreamReader reader = XMLInputFactory.newFactory()
                .createXMLStreamReader(new StringReader(xml));
        reader.nextTag();
        reader.nextTag();
        return reader;
    }

    private static MessagePartInfo newPart(Class<?> type, String localName) {
        ServiceInfo service = new ServiceInfo();
        service.setName(new QName(NAMESPACE, "ExceptionService"));
        InterfaceInfo iface = service.createInterface(new QName(NAMESPACE, "ExceptionPort"));
        OperationInfo operation = iface.addOperation(new QName(NAMESPACE, "invoke" + localName));
        MessageInfo message = operation.createMessage(
                new QName(NAMESPACE, localName + "Message"), MessageInfo.Type.OUTPUT);
        operation.setOutput(localName + "Message", message);
        MessagePartInfo part = message.addMessagePart(new QName(NAMESPACE, localName));
        part.setElementQName(new QName(NAMESPACE, localName));
        part.setConcreteName(new QName(NAMESPACE, localName));
        part.setTypeClass(type);
        part.setProperty(JAXBDataBinding.class.getName() + ".CUSTOM_EXCEPTION", Boolean.TRUE);
        return part;
    }

    public interface CollectionService {
        ValueList<String> listValues();

        ValueSet<String> setValues();
    }

    public static class ValueList<T> extends ArrayList<T> {
        public ValueList() {
        }
    }

    public static class ValueSet<T> extends LinkedHashSet<T> {
        public ValueSet() {
        }
    }

    @XmlRegistry
    public static class TokenObjectFactory {
        public TokenObjectFactory() {
        }

        @XmlElementDecl(namespace = NAMESPACE, name = "tokens")
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public JAXBElement<List<String>> createTokens(List<String> value) {
            return new JAXBElement(new QName(NAMESPACE, "tokens"), List.class, value);
        }
    }

    public static class StringGenericArrayType implements GenericArrayType {
        @Override
        public Type getGenericComponentType() {
            return String.class;
        }
    }

    public static class NamespaceAwareReader extends StreamReaderDelegate {
        private final NamespaceContext context;

        public NamespaceAwareReader(XMLStreamReader reader) {
            super(reader);
            context = new XercesStyleNamespaceContext(reader.getNamespaceContext());
        }

        @Override
        public NamespaceContext getNamespaceContext() {
            return context;
        }
    }

    public static class XercesStyleNamespaceContext implements NamespaceContext {
        private final PrefixStore fNamespaceContext = new PrefixStore();
        private final NamespaceContext delegate;

        public XercesStyleNamespaceContext(NamespaceContext delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getNamespaceURI(String prefix) {
            return delegate.getNamespaceURI(prefix);
        }

        @Override
        public String getPrefix(String namespaceUri) {
            return delegate.getPrefix(namespaceUri);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<String> getPrefixes(String namespaceUri) {
            return delegate.getPrefixes(namespaceUri);
        }
    }

    public static class PrefixStore {
        public Enumeration<String> getAllPrefixes() {
            return Collections.enumeration(Collections.singleton("t"));
        }
    }

    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static class PropertyException extends Exception {
        private String detail;
        private String[] tags;

        public PropertyException() {
        }

        public PropertyException(String message) {
            super(message);
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }

        public String[] getTags() {
            return tags;
        }

        public void setTags(String[] tags) {
            this.tags = Arrays.copyOf(tags, tags.length);
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class FieldException extends Exception {
        public String detail;
        public String[] labels;

        public FieldException() {
        }
    }

    public static class PublicFaultFields extends Exception {
        public int[] codes;

        public PublicFaultFields() {
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class InheritedFieldException extends PublicFaultFields {
        public InheritedFieldException() {
        }
    }

    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static class BooleanPropertyException extends Exception {
        private boolean retryable;
        private int[] codes;

        public BooleanPropertyException() {
        }

        public boolean isRetryable() {
            return retryable;
        }

        public void setRetryable(boolean retryable) {
            this.retryable = retryable;
        }

        public int[] getCodes() {
            return codes;
        }

        public void setCodes(int[] codes) {
            this.codes = codes.clone();
        }
    }

    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static class StringConstructorException extends Exception {
        private String code;

        public StringConstructorException(String message) {
            super(message);
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }
}
