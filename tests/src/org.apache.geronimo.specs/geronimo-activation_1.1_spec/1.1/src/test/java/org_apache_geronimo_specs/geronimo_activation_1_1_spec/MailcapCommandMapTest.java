/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_activation_1_1_spec;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import javax.activation.CommandInfo;
import javax.activation.DataContentHandler;
import javax.activation.DataSource;
import javax.activation.MailcapCommandMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MailcapCommandMapTest {
    @Test
    void createsConfiguredDataContentHandler() throws IOException {
        String mimeType = "application/x-geronimo-activation-test";
        String mailcap = mimeType + ";; x-java-content-handler=" + TestDataContentHandler.class.getName();
        MailcapCommandMap commandMap = new MailcapCommandMap(
                new ByteArrayInputStream(mailcap.getBytes(StandardCharsets.UTF_8)));

        CommandInfo command = commandMap.getCommand(mimeType, "content-handler");
        DataContentHandler handler = commandMap.createDataContentHandler(mimeType);

        assertThat(command.getCommandClass()).isEqualTo(TestDataContentHandler.class.getName());
        assertThat(handler).isInstanceOf(TestDataContentHandler.class);
        assertThat(handler.getContent(null)).isEqualTo("configured-handler");
    }

    public static final class TestDataContentHandler implements DataContentHandler {
        public TestDataContentHandler() {
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[0];
        }

        @Override
        public Object getTransferData(DataFlavor flavor, DataSource dataSource)
                throws UnsupportedFlavorException, IOException {
            throw new UnsupportedFlavorException(flavor);
        }

        @Override
        public Object getContent(DataSource dataSource) {
            return "configured-handler";
        }

        @Override
        public void writeTo(Object object, String mimeType, OutputStream outputStream) throws IOException {
            outputStream.write(object.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
