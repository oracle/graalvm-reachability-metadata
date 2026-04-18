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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class MailcapCommandMapTest {
    @Test
    void createDataContentHandlerInstantiatesHandlerUsingContextClassLoader() {
        MailcapCommandMap commandMap = new MailcapCommandMap();
        commandMap.addMailcap(
            "text/x-context-loader;; x-java-content-handler=" + PreferredDataContentHandler.class.getName()
        );

        DataContentHandler dataContentHandler = commandMap.createDataContentHandler("text/x-context-loader");

        assertInstanceOf(PreferredDataContentHandler.class, dataContentHandler);
    }

    @Test
    void createDataContentHandlerFallsBackToClassForNameWhenContextLoaderCannotLoadHandler() {
        MailcapCommandMap commandMap = new MailcapCommandMap();
        commandMap.addMailcap(
            "text/x-class-for-name;; x-java-content-handler=" + FallbackDataContentHandler.class.getName()
        );

        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(new RejectingContextClassLoader(originalContextClassLoader));
        try {
            DataContentHandler dataContentHandler = commandMap.createDataContentHandler("text/x-class-for-name");

            assertInstanceOf(FallbackDataContentHandler.class, dataContentHandler);
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    public static final class PreferredDataContentHandler implements DataContentHandler {
        public PreferredDataContentHandler() {
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

    public static final class FallbackDataContentHandler implements DataContentHandler {
        public FallbackDataContentHandler() {
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

    private static final class RejectingContextClassLoader extends ClassLoader {
        private RejectingContextClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.equals(FallbackDataContentHandler.class.getName())) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
