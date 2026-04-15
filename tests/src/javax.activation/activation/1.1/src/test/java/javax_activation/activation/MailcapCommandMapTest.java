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
import java.nio.charset.StandardCharsets;

import javax.activation.DataContentHandler;
import javax.activation.DataSource;
import javax.activation.MailcapCommandMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MailcapCommandMapTest {
    @Test
    void createDataContentHandlerLoadsAndInstantiatesRegisteredHandler() {
        MailcapCommandMap commandMap = new MailcapCommandMap();
        commandMap.addMailcap(
                "application/x-loadable;; x-java-content-handler=" + LoadableDataContentHandler.class.getName());

        DataContentHandler handler = commandMap.createDataContentHandler("application/x-loadable");

        assertThat(handler).isInstanceOf(LoadableDataContentHandler.class);
    }

    @Test
    void createDataContentHandlerFallsBackToClassForNameWhenContextLoaderCannotLoadHandler() {
        MailcapCommandMap commandMap = new MailcapCommandMap();
        commandMap.addMailcap(
                "application/x-fallback;; x-java-content-handler=" + FallbackDataContentHandler.class.getName());

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader failingClassLoader = new ClassLoader(originalClassLoader) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (FallbackDataContentHandler.class.getName().equals(name)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name);
            }
        };

        try {
            Thread.currentThread().setContextClassLoader(failingClassLoader);

            DataContentHandler handler =
                    commandMap.createDataContentHandler("application/x-fallback");

            assertThat(handler).isInstanceOf(FallbackDataContentHandler.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public static class LoadableDataContentHandler implements DataContentHandler {
        private static final DataFlavor[] DATA_FLAVORS = { new DataFlavor(String.class, "text") };

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return DATA_FLAVORS;
        }

        @Override
        public Object getTransferData(DataFlavor dataFlavor, DataSource dataSource)
                throws UnsupportedFlavorException, IOException {
            if (!DATA_FLAVORS[0].equals(dataFlavor)) {
                throw new UnsupportedFlavorException(dataFlavor);
            }
            return getContent(dataSource);
        }

        @Override
        public Object getContent(DataSource dataSource) throws IOException {
            return dataSource == null ? "" : dataSource.getContentType();
        }

        @Override
        public void writeTo(Object object, String mimeType, OutputStream outputStream) throws IOException {
            outputStream.write(String.valueOf(object).getBytes(StandardCharsets.UTF_8));
        }
    }

    public static class FallbackDataContentHandler extends LoadableDataContentHandler {
    }
}
