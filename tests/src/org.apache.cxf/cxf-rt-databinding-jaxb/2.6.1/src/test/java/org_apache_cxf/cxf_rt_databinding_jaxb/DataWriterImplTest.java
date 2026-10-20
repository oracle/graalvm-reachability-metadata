/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_databinding_jaxb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.util.Arrays;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import org.apache.cxf.databinding.DataWriter;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.apache.cxf.service.model.InterfaceInfo;
import org.apache.cxf.service.model.MessageInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.junit.jupiter.api.Test;

public class DataWriterImplTest {
    @Test
    void writesCollectionForArrayMessagePart() throws Exception {
        JaxbTestSupport.disableOptimizedAccessors();
        JAXBDataBinding binding = new JAXBDataBinding(String[].class);
        MessagePartInfo part = arrayPart();
        DataWriter<XMLStreamWriter> dataWriter = binding.createWriter(XMLStreamWriter.class);
        StringWriter output = new StringWriter();
        XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output);

        dataWriter.write(Arrays.asList("first", "second"), part, writer);
        writer.close();

        assertThat(output.toString()).contains("values", "first", "second");
    }

    private static MessagePartInfo arrayPart() {
        String namespace = "urn:cxf-writer-test";
        ServiceInfo service = new ServiceInfo();
        service.setName(new QName(namespace, "WriterService"));
        InterfaceInfo iface = service.createInterface(new QName(namespace, "WriterPort"));
        OperationInfo operation = iface.addOperation(new QName(namespace, "write"));
        MessageInfo message = operation.createMessage(
                new QName(namespace, "writeRequest"), MessageInfo.Type.INPUT);
        operation.setInput("writeRequest", message);
        MessagePartInfo part = message.addMessagePart(new QName(namespace, "values"));
        part.setConcreteName(new QName(namespace, "values"));
        part.setTypeClass(String[].class);
        return part;
    }
}
