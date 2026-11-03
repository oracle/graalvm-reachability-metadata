/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb_jaxb_runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlMimeType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.attachment.AttachmentMarshaller;
import jakarta.xml.bind.attachment.AttachmentUnmarshaller;
import org.glassfish.jaxb.runtime.api.JAXBRIContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises JAXB runtime's MTOM attachment subsystem for a document delivery workflow.
public class MtomAttachmentDeliveryTest {

    private static final byte[] PAYLOAD = {0, 1, 2, 3, 4, 5};
    private static final String CONTENT_ID = "cid:payload@example.test";

    @Test
    @Timeout(60)
    void exchangesBinaryPayloadsAsXopAttachments() throws Exception {
        JAXBContext context = JAXBRIContext.newInstance(
                new Class<?>[] {Delivery.class}, List.of(), null, null, false, null,
                true, false, false, false);
        Marshaller marshaller = context.createMarshaller();
        RecordingAttachmentMarshaller attachments = new RecordingAttachmentMarshaller();
        marshaller.setAttachmentMarshaller(attachments);

        Delivery delivery = new Delivery(new ByteArrayDataSource(PAYLOAD, "application/octet-stream"));
        StringWriter output = new StringWriter();
        marshaller.marshal(delivery, output);

        String xml = output.toString();
        assertTrue(xml.contains("<xop:Include"));
        assertTrue(xml.contains("href=\"" + CONTENT_ID + "\""));
        assertEquals(List.of(CONTENT_ID), attachments.contentIds);

        Unmarshaller unmarshaller = context.createUnmarshaller();
        unmarshaller.setAttachmentUnmarshaller(new RecordingAttachmentUnmarshaller());
        Delivery restored = assertInstanceOf(Delivery.class,
                unmarshaller.unmarshal(new StringReader(xml)));
        assertEquals("application/octet-stream", restored.payload.getContentType());
        assertArrayEquals(PAYLOAD, readBytes(restored.payload));
    }

    private static byte[] readBytes(DataHandler dataHandler) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        dataHandler.writeTo(output);
        return output.toByteArray();
    }

    public static final class RecordingAttachmentMarshaller extends AttachmentMarshaller {

        private final java.util.ArrayList<String> contentIds = new java.util.ArrayList<>();

        @Override
        public String addMtomAttachment(DataHandler data, String elementNamespace, String elementLocalName) {
            contentIds.add(CONTENT_ID);
            return CONTENT_ID;
        }

        @Override
        public String addMtomAttachment(byte[] data, int offset, int length, String mimeType,
                String elementNamespace, String elementLocalName) {
            contentIds.add(CONTENT_ID);
            return CONTENT_ID;
        }

        @Override
        public String addSwaRefAttachment(DataHandler data) {
            contentIds.add(CONTENT_ID);
            return CONTENT_ID;
        }

        @Override
        public boolean isXOPPackage() {
            return true;
        }
    }

    public static final class RecordingAttachmentUnmarshaller extends AttachmentUnmarshaller {

        @Override
        public DataHandler getAttachmentAsDataHandler(String cid) {
            assertEquals(CONTENT_ID, cid);
            return new DataHandler(new ByteArrayDataSource(PAYLOAD, "application/octet-stream"));
        }

        @Override
        public byte[] getAttachmentAsByteArray(String cid) {
            assertEquals(CONTENT_ID, cid);
            return PAYLOAD.clone();
        }

        @Override
        public boolean isXOPPackage() {
            return true;
        }
    }

    @XmlRootElement(name = "delivery")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Delivery {

        @XmlElement(required = true)
        @XmlMimeType("application/octet-stream")
        private DataHandler payload;

        public Delivery() {
        }

        private Delivery(DataSource payload) {
            this.payload = new DataHandler(payload);
        }
    }

    public static final class ByteArrayDataSource implements DataSource {

        private final byte[] bytes;
        private final String contentType;

        private ByteArrayDataSource(byte[] bytes, String contentType) {
            this.bytes = bytes.clone();
            this.contentType = contentType;
        }

        @Override
        public ByteArrayInputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            throw new UnsupportedOperationException("read-only payload");
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getName() {
            return "delivery-payload";
        }
    }
}
