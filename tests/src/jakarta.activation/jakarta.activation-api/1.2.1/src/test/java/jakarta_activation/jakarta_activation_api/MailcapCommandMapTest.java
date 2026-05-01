/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_activation.jakarta_activation_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.OutputStream;

import javax.activation.DataContentHandler;
import javax.activation.DataSource;
import javax.activation.MailcapCommandMap;

import org.junit.jupiter.api.Test;

public class MailcapCommandMapTest {

    private static final String TEST_MIME_TYPE = "application/x-mailcap-command-map-test";

    @Test
    void createDataContentHandlerInstantiatesProgrammaticMailcapHandler() {
        MailcapCommandMap commandMap = new MailcapCommandMap();
        commandMap.addMailcap(mailcapEntry(TestDataContentHandler.class));

        DataContentHandler handler = commandMap.createDataContentHandler(TEST_MIME_TYPE);

        assertThat(handler).isInstanceOf(TestDataContentHandler.class);
        assertThat(TestDataContentHandler.class.cast(handler).created).isTrue();
    }

    @Test
    void createDataContentHandlerFallsBackToClassForNameWhenContextLoaderCannotLoadHandler() {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousContextClassLoader = currentThread.getContextClassLoader();
        ClassLoader failingContextClassLoader = new HandlerRejectingClassLoader(
                previousContextClassLoader,
                TestDataContentHandler.class.getName());
        currentThread.setContextClassLoader(failingContextClassLoader);
        try {
            MailcapCommandMap commandMap = new MailcapCommandMap();
            commandMap.addMailcap(mailcapEntry(TestDataContentHandler.class));

            DataContentHandler handler = commandMap.createDataContentHandler(TEST_MIME_TYPE);

            assertThat(handler).isInstanceOf(TestDataContentHandler.class);
        } finally {
            currentThread.setContextClassLoader(previousContextClassLoader);
        }
    }

    private static String mailcapEntry(Class<? extends DataContentHandler> handlerClass) {
        return TEST_MIME_TYPE + "; ; x-java-content-handler=" + handlerClass.getName();
    }

    public static final class TestDataContentHandler implements DataContentHandler {

        private final boolean created;

        public TestDataContentHandler() {
            created = true;
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
        public Object getContent(DataSource dataSource) throws IOException {
            return null;
        }

        @Override
        public void writeTo(Object object, String mimeType, OutputStream outputStream) throws IOException {
        }
    }

    private static final class HandlerRejectingClassLoader extends ClassLoader {

        private final String rejectedClassName;

        private HandlerRejectingClassLoader(ClassLoader parent, String rejectedClassName) {
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
