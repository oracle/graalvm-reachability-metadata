/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stax.StAXSource;

import org.apache.cxf.databinding.source.XMLStreamDataReader;
import org.junit.jupiter.api.Test;

public class XMLStreamDataReaderTest {
    @Test
    void createsJaxpStaxSourceFromXmlStream() throws Exception {
        XMLStreamReader stream =
                XMLInputFactory.newInstance()
                        .createXMLStreamReader(new StringReader("<message>hello</message>"));
        stream.nextTag();
        XMLStreamDataReader reader = new XMLStreamDataReader();

        StAXSource source = (StAXSource) reader.read(null, stream, StAXSource.class);

        assertThat(source.getXMLStreamReader()).isSameAs(stream);
        assertThat(source.getXMLStreamReader().getLocalName()).isEqualTo("message");
    }
}
