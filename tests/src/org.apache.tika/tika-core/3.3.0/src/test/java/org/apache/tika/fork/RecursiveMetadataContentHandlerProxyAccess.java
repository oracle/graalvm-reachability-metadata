/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.tika.fork;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BasicContentHandlerFactory.HANDLER_TYPE;

public final class RecursiveMetadataContentHandlerProxyAccess {

    private RecursiveMetadataContentHandlerProxyAccess() {
    }

    public static int serializedMainDocumentMetadataLength() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        RecursiveMetadataContentHandlerProxy proxy = new RecursiveMetadataContentHandlerProxy(
                0, new BasicContentHandlerFactory(HANDLER_TYPE.IGNORE, -1));
        proxy.init(new DataInputStream(new ByteArrayInputStream(new byte[0])), output);

        proxy.endDocument(new DefaultHandler(), new Metadata());
        output.flush();

        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        requireByte(input, ForkServer.RESOURCE, "resource marker");
        requireByte(input, 0, "resource id");
        requireByte(input, RecursiveMetadataContentHandlerProxy.MAIN_DOCUMENT, "document marker");
        requireByte(input, RecursiveMetadataContentHandlerProxy.METADATA_ONLY, "payload marker");
        int metadataLength = input.readInt();
        if (metadataLength <= 0) {
            throw new IllegalStateException("Expected serialized metadata bytes");
        }
        return metadataLength;
    }

    private static void requireByte(DataInputStream input, int expected, String field)
            throws Exception {
        int actual = input.readUnsignedByte();
        if (actual != expected) {
            throw new IllegalStateException(
                    "Expected " + field + " " + expected + ", but got " + actual);
        }
    }
}
