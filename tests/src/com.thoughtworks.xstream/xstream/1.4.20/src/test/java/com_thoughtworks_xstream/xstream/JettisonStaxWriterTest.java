/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.StringWriter;
import java.util.List;

import javax.xml.stream.XMLStreamWriter;

import com.thoughtworks.xstream.io.json.JettisonStaxWriter;
import com.thoughtworks.xstream.io.xml.QNameMap;

import org.codehaus.jettison.mapped.Configuration;
import org.codehaus.jettison.mapped.MappedNamespaceConvention;
import org.codehaus.jettison.mapped.MappedXMLOutputFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JettisonStaxWriterTest {
    @Test
    void writesCollectionTypedNodeAsJsonArray() throws Exception {
        StringWriter output = new StringWriter();
        Configuration configuration = new Configuration();
        MappedNamespaceConvention convention = new MappedNamespaceConvention(configuration);
        MappedXMLOutputFactory outputFactory = new MappedXMLOutputFactory(configuration);
        XMLStreamWriter streamWriter = outputFactory.createXMLStreamWriter(output);
        JettisonStaxWriter writer = new JettisonStaxWriter(new QNameMap(), streamWriter, convention);

        writer.startNode("items", List.class);
        writer.setValue("alpha");
        writer.endNode();
        writer.close();

        assertThat(output.toString())
            .contains("\"items\"")
            .contains("[")
            .contains("alpha");
    }
}
