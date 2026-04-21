/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.activation;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.OutputStream;

import javax.activation.DataContentHandler;
import javax.activation.DataSource;

public class MailcapCommandMapDataContentHandler implements DataContentHandler {
    public MailcapCommandMapDataContentHandler() {
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
        return null;
    }

    @Override
    public void writeTo(Object object, String mimeType, OutputStream outputStream) throws IOException {
        outputStream.write(new byte[0]);
    }
}
