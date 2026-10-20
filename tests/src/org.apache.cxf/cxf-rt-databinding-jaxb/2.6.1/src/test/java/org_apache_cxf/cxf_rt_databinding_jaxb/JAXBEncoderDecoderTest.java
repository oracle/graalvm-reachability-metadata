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
import java.util.Arrays;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.apache.cxf.jaxb.JAXBEncoderDecoder;
import org.apache.cxf.service.model.InterfaceInfo;
import org.apache.cxf.service.model.MessageInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.junit.jupiter.api.Test;

public class JAXBEncoderDecoderTest {
    private static final String NAMESPACE = "urn:cxf-jaxb-test";

    @Test
    void marshalsAndUnmarshalsCustomExceptionProperties() throws Exception {
        JAXBContext context = JAXBContext.newInstance(String.class);
        MessagePartInfo part = newPart(PropertyException.class, "fault");
        PropertyException original = new PropertyException();
        original.setDetail("invalid request");
        original.setTags(new String[] {"client", "validation"});

        StringWriter output = new StringWriter();
        XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
        outputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);
        XMLStreamWriter writer = outputFactory.createXMLStreamWriter(output);
        JAXBEncoderDecoder.marshallException(context.createMarshaller(), original, part, writer);
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
    void usesStringConstructorWhenRestoringException() throws Exception {
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
    void unmarshalsWithSchemaValidation() throws Exception {
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

    private static MessagePartInfo newPart(Class<?> type, String localName) {
        ServiceInfo service = new ServiceInfo();
        service.setName(new QName(NAMESPACE, "ExceptionService"));
        InterfaceInfo iface = service.createInterface(new QName(NAMESPACE, "ExceptionPort"));
        OperationInfo operation = iface.addOperation(new QName(NAMESPACE, "invoke" + localName));
        MessageInfo message = operation.createMessage(
                new QName(NAMESPACE, localName + "Message"), MessageInfo.Type.OUTPUT);
        MessagePartInfo part = message.addMessagePart(new QName(NAMESPACE, localName));
        part.setElementQName(new QName(NAMESPACE, localName));
        part.setConcreteName(new QName(NAMESPACE, localName));
        part.setTypeClass(type);
        part.setProperty(JAXBDataBinding.class.getName() + ".CUSTOM_EXCEPTION", Boolean.TRUE);
        return part;
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
