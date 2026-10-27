/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.pulsar.shade.javax.activation.DataContentHandler;
import org.apache.pulsar.shade.javax.activation.DataSource;
import org.apache.pulsar.shade.javax.activation.MailcapCommandMap;
import org.junit.jupiter.api.Test;

public class MailcapCommandMapTest {
    @Test
    void loadsAConfiguredContentHandler() {
        MailcapCommandMap commandMap = new MailcapCommandMap();
        commandMap.addMailcap(
                "application/x-pulsar;; x-java-content-handler=" + TestContentHandler.class.getName());

        DataContentHandler handler = commandMap.createDataContentHandler("application/x-pulsar");

        assertThat(handler).isInstanceOf(TestContentHandler.class);
    }

    public static class TestContentHandler implements DataContentHandler {
        public TestContentHandler() {
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[0];
        }

        @Override
        public Object getTransferData(DataFlavor flavor, DataSource source)
                throws UnsupportedFlavorException {
            throw new UnsupportedFlavorException(flavor);
        }

        @Override
        public Object getContent(DataSource source) {
            return "pulsar";
        }

        @Override
        public void writeTo(Object value, String mimeType, OutputStream output) throws IOException {
            output.write(value.toString().getBytes());
        }
    }
}
