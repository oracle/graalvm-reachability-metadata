/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.activation;

import java.awt.datatransfer.DataFlavor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Permission;

import javax.activation.DataContentHandler;
import javax.activation.DataContentHandlerFactory;
import javax.activation.DataHandler;
import javax.activation.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

public class DataHandlerTest {
    private static final String TEST_MIME_TYPE = "application/x-data-handler-test";

    @Test
    @DisabledIfSystemProperty(named = "org.graalvm.nativeimage.imagecode", matches = "runtime")
    void setDataContentHandlerFactoryHandlesDeniedSetFactoryFromSameClassLoader() throws IOException {
        SecurityManager originalSecurityManager = System.getSecurityManager();

        try {
            System.setSecurityManager(new DenyingSetFactorySecurityManager());
            DataHandler.setDataContentHandlerFactory(new TestDataContentHandlerFactory());
        } finally {
            System.setSecurityManager(originalSecurityManager);
        }

        DataHandler dataHandler = new DataHandler("payload", TEST_MIME_TYPE);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        dataHandler.writeTo(outputStream);

        assertThat(dataHandler.isDataFlavorSupported(TestDataContentHandler.DATA_FLAVOR)).isTrue();
        assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo("payload");
    }

    private static final class DenyingSetFactorySecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
        }

        @Override
        public void checkPermission(Permission permission, Object context) {
        }

        @Override
        public void checkSetFactory() {
            throw new SecurityException("setFactory denied for test coverage");
        }
    }

    private static final class TestDataContentHandlerFactory implements DataContentHandlerFactory {
        @Override
        public DataContentHandler createDataContentHandler(String mimeType) {
            if (TEST_MIME_TYPE.equals(mimeType)) {
                return new TestDataContentHandler();
            }
            return null;
        }
    }

    private static final class TestDataContentHandler implements DataContentHandler {
        private static final DataFlavor DATA_FLAVOR = new DataFlavor(String.class, "payload");

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {DATA_FLAVOR };
        }

        @Override
        public Object getTransferData(DataFlavor dataFlavor, DataSource dataSource) {
            return null;
        }

        @Override
        public Object getContent(DataSource dataSource) {
            return null;
        }

        @Override
        public void writeTo(Object object, String mimeType, OutputStream outputStream) throws IOException {
            outputStream.write(String.valueOf(object).getBytes(StandardCharsets.UTF_8));
        }
    }
}
