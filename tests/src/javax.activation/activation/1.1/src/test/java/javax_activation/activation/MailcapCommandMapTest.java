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
import javax.activation.DataContentHandler;
import javax.activation.DataSource;
import javax.activation.MailcapCommandMap;
import org.junit.jupiter.api.Test;

class MailcapCommandMapTest {
    @Test
    void createDataContentHandlerUsesContextClassLoaderWhenItCanLoadTheHandler() {
        MailcapCommandMap commandMap = new MailcapCommandMap();
        commandMap.addMailcap(
                "application/x-mailcap-loadclass;; x-java-content-handler=" + TestDataContentHandler.class.getName()
        );

        DataContentHandler dataContentHandler = commandMap.createDataContentHandler("application/x-mailcap-loadclass");

        assertThat(dataContentHandler).isInstanceOf(TestDataContentHandler.class);
    }

    @Test
    void createDataContentHandlerFallsBackToClassForNameWhenContextClassLoaderFails() {
        MailcapCommandMap commandMap = new MailcapCommandMap();
        commandMap.addMailcap(
                "application/x-mailcap-forname;; x-java-content-handler=" + TestDataContentHandler.class.getName()
        );

        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        ClassLoader rejectingClassLoader = new RejectingClassLoader(originalClassLoader, TestDataContentHandler.class.getName());
        thread.setContextClassLoader(rejectingClassLoader);
        try {
            DataContentHandler dataContentHandler = commandMap.createDataContentHandler("application/x-mailcap-forname");

            assertThat(dataContentHandler).isInstanceOf(TestDataContentHandler.class);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    public static final class TestDataContentHandler implements DataContentHandler {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[0];
        }

        @Override
        public Object getTransferData(DataFlavor dataFlavor, DataSource dataSource)
                throws UnsupportedFlavorException, IOException {
            throw new UnsupportedFlavorException(dataFlavor);
        }

        @Override
        public Object getContent(DataSource dataSource) {
            return dataSource;
        }

        @Override
        public void writeTo(Object object, String mimeType, OutputStream outputStream) {
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingClassLoader(ClassLoader parent, String rejectedClassName) {
            super(parent);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
