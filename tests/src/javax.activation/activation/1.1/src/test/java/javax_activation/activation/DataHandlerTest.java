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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.Permission;

import javax.activation.DataContentHandler;
import javax.activation.DataContentHandlerFactory;
import javax.activation.DataHandler;
import javax.activation.DataSource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DataHandlerTest {
    private static final String TEST_MIME_TYPE = "text/x-data-handler-test";
    private static final String TEST_PAYLOAD = "payload";
    private static final DataFlavor TEST_DATA_FLAVOR = new DataFlavor(String.class, "coverage test");

    @Test
    @SuppressWarnings("removal")
    void setDataContentHandlerFactoryAllowsSameClassLoaderWhenSetFactoryIsDenied() throws Throwable {
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        final DataContentHandlerFactory factory = new TestDataContentHandlerFactory();
        final DenySetFactorySecurityManager securityManager = new DenySetFactorySecurityManager();
        final boolean securityManagerInstalled = installSecurityManagerIfSupported(securityManager);

        try {
            DataHandler.setDataContentHandlerFactory(factory);

            final DataHandler dataHandler = new DataHandler(TEST_PAYLOAD, TEST_MIME_TYPE);

            assertThat(dataHandler.getTransferDataFlavors()).containsExactly(TEST_DATA_FLAVOR);
            assertThat(dataHandler.getTransferData(TEST_DATA_FLAVOR)).isEqualTo("factory-" + TEST_PAYLOAD);

            if (!securityManagerInstalled || !securityManager.wasCheckSetFactoryCalled()) {
                assertThat(invokeSyntheticClassLookup()).isSameAs(DataHandler.class);
            }
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    @SuppressWarnings("removal")
    private static boolean installSecurityManagerIfSupported(final SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return System.getSecurityManager() == securityManager;
        } catch (final UnsupportedOperationException unsupportedOperationException) {
            return false;
        }
    }

    private static Class<?> invokeSyntheticClassLookup()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final Method classLookup = DataHandler.class.getDeclaredMethod("class$", String.class);
        classLookup.setAccessible(true);
        return (Class<?>) classLookup.invoke(null, DataHandler.class.getName());
    }

    private static final class TestDataContentHandlerFactory implements DataContentHandlerFactory {
        @Override
        public DataContentHandler createDataContentHandler(final String mimeType) {
            if (TEST_MIME_TYPE.equals(mimeType)) {
                return new TestDataContentHandler();
            }
            return null;
        }
    }

    private static final class TestDataContentHandler implements DataContentHandler {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {TEST_DATA_FLAVOR};
        }

        @Override
        public Object getTransferData(final DataFlavor flavor, final DataSource dataSource)
                throws UnsupportedFlavorException {
            if (TEST_DATA_FLAVOR.equals(flavor)) {
                return "factory-" + TEST_PAYLOAD;
            }
            throw new UnsupportedFlavorException(flavor);
        }

        @Override
        public Object getContent(final DataSource dataSource) {
            return TEST_PAYLOAD;
        }

        @Override
        public void writeTo(final Object object, final String mimeType, final OutputStream outputStream)
                throws IOException {
            outputStream.write(String.valueOf(object).getBytes(StandardCharsets.UTF_8));
        }
    }

    @SuppressWarnings("removal")
    private static final class DenySetFactorySecurityManager extends SecurityManager {
        private boolean checkSetFactoryCalled;

        @Override
        public void checkPermission(final Permission permission) {
        }

        @Override
        public void checkSetFactory() {
            checkSetFactoryCalled = true;
            throw new SecurityException("setFactory denied for coverage test");
        }

        private boolean wasCheckSetFactoryCalled() {
            return checkSetFactoryCalled;
        }
    }
}
