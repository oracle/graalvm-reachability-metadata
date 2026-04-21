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
import javax.activation.MailcapCommandMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MailcapCommandMapTest {
    @Test
    void createDataContentHandlerLoadsHandlerFromContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader loadingClassLoader = new ClassLoader(MailcapCommandMapTest.class.getClassLoader()) {
        };

        Thread.currentThread().setContextClassLoader(loadingClassLoader);
        try {
            MailcapCommandMap commandMap = new MailcapCommandMap();
            commandMap.addMailcap(
                "application/x-mailcap-test-load;; x-java-content-handler=" + SampleDataContentHandler.class.getName()
            );

            DataContentHandler handler = commandMap.createDataContentHandler("application/x-mailcap-test-load");

            assertThat(handler).isInstanceOf(SampleDataContentHandler.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void createDataContentHandlerFallsBackToClassForName() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader blockingClassLoader = new ClassLoader(MailcapCommandMapTest.class.getClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (SampleDataContentHandler.class.getName().equals(name)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name);
            }
        };

        Thread.currentThread().setContextClassLoader(blockingClassLoader);
        try {
            MailcapCommandMap commandMap = new MailcapCommandMap();
            commandMap.addMailcap(
                "application/x-mailcap-test-fallback;; x-java-content-handler=" + SampleDataContentHandler.class.getName()
            );

            DataContentHandler handler = commandMap.createDataContentHandler("application/x-mailcap-test-fallback");

            assertThat(handler).isInstanceOf(SampleDataContentHandler.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public static final class SampleDataContentHandler implements DataContentHandler {
        public SampleDataContentHandler() {
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
}
