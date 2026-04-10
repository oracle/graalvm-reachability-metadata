/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.activation;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Permission;
import javax.activation.ActivationDataFlavor;
import javax.activation.DataContentHandler;
import javax.activation.DataContentHandlerFactory;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import org.junit.jupiter.api.Test;

class DataHandlerTest {
    private static final String MIME_TYPE = "application/x-datahandler-factory";

    @Test
    @SuppressWarnings("removal")
    void setDataContentHandlerFactoryFallsBackToDataHandlerClassLookupWhenCheckSetFactoryIsRejected() {
        TrackingDataContentHandlerFactory factory = new TrackingDataContentHandlerFactory();

        assertThat(factory.getClass().getClassLoader()).isSameAs(DataHandler.class.getClassLoader());

        SecurityManager originalSecurityManager = System.getSecurityManager();
        System.setSecurityManager(new RejectingSetFactorySecurityManager());
        try {
            DataHandler.setDataContentHandlerFactory(factory);
        } finally {
            System.setSecurityManager(originalSecurityManager);
        }

        DataHandler dataHandler = new DataHandler("payload", MIME_TYPE);
        DataFlavor[] transferFlavors = dataHandler.getTransferDataFlavors();

        assertThat(factory.wasUsed()).isTrue();
        assertThat(transferFlavors).hasSize(1);
        assertThat(transferFlavors[0].getRepresentationClass()).isEqualTo(DataSource.class);
        assertThat(transferFlavors[0].isMimeTypeEqual(MIME_TYPE)).isTrue();
    }

    private static final class TrackingDataContentHandlerFactory implements DataContentHandlerFactory {
        private boolean used;

        @Override
        public DataContentHandler createDataContentHandler(String mimeType) {
            if (!MIME_TYPE.equals(mimeType)) {
                return null;
            }
            used = true;
            return new TestDataContentHandler();
        }

        private boolean wasUsed() {
            return used;
        }
    }

    private static final class TestDataContentHandler implements DataContentHandler {
        private static final ActivationDataFlavor FLAVOR =
                new ActivationDataFlavor(DataSource.class, MIME_TYPE, MIME_TYPE);

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{FLAVOR};
        }

        @Override
        public Object getTransferData(DataFlavor dataFlavor, DataSource dataSource)
                throws UnsupportedFlavorException {
            if (!FLAVOR.equals(dataFlavor)) {
                throw new UnsupportedFlavorException(dataFlavor);
            }
            return dataSource;
        }

        @Override
        public Object getContent(DataSource dataSource) {
            return dataSource;
        }

        @Override
        public void writeTo(Object object, String mimeType, OutputStream outputStream) throws IOException {
            outputStream.write(String.valueOf(object).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class RejectingSetFactorySecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
        }

        @Override
        public void checkSetFactory() {
            throw new SecurityException("rejected by test security manager");
        }
    }
}
