/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_dataformat.jackson_dataformat_xml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.dataformat.xml.XmlFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlFactoryTest {
    @Test
    void serializationReconstructsConfiguredStaxFactories() throws Exception {
        XmlFactory factory = XmlFactory.builder()
                .nameForTextElement("content")
                .build();

        XmlFactory restoredFactory = roundTrip(factory);

        assertThat(restoredFactory).isNotSameAs(factory);
        assertThat(restoredFactory.getFormatName()).isEqualTo(XmlFactory.FORMAT_NAME_XML);
        assertThat(restoredFactory.getXMLTextElementName()).isEqualTo("content");
        assertThat(restoredFactory.getXMLInputFactory()).isNotSameAs(factory.getXMLInputFactory());
        assertThat(restoredFactory.getXMLInputFactory().getClass())
                .isEqualTo(factory.getXMLInputFactory().getClass());
        assertThat(restoredFactory.getXMLOutputFactory()).isNotSameAs(factory.getXMLOutputFactory());
        assertThat(restoredFactory.getXMLOutputFactory().getClass())
                .isEqualTo(factory.getXMLOutputFactory().getClass());
    }

    private static XmlFactory roundTrip(XmlFactory factory) throws Exception {
        ByteArrayOutputStream serialized = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(serialized)) {
            output.writeObject(factory);
        }

        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(serialized.toByteArray()))) {
            return (XmlFactory) input.readObject();
        }
    }
}
