/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.javax_activation_api;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.OutputStream;

import javax.activation.DataContentHandler;
import javax.activation.DataSource;
import javax.activation.MailcapCommandMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailcapCommandMapTest {
    @Test
    void createDataContentHandlerFallsBackToClassForNameWhenContextClassLoaderCannotLoadHandler() {
        MailcapCommandMap commandMap = new MailcapCommandMap();
        commandMap.addMailcap("text/x-mailcap-command-map; ; x-java-content-handler="
                + RecordingDataContentHandler.class.getName());

        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new RejectingClassLoader(
                originalContextClassLoader,
                RecordingDataContentHandler.class.getName()));
        try {
            DataContentHandler handler = commandMap.createDataContentHandler("text/x-mailcap-command-map");

            assertThat(handler).isInstanceOf(RecordingDataContentHandler.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    public static final class RecordingDataContentHandler implements DataContentHandler {
        public RecordingDataContentHandler() {
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[0];
        }

        @Override
        public Object getTransferData(DataFlavor dataFlavor, DataSource dataSource)
                throws UnsupportedFlavorException, IOException {
            return null;
        }

        @Override
        public Object getContent(DataSource dataSource) throws IOException {
            return null;
        }

        @Override
        public void writeTo(Object object, String mimeType, OutputStream outputStream) throws IOException {
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingClassLoader(ClassLoader parent, String rejectedClassName) {
            super(parent);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
