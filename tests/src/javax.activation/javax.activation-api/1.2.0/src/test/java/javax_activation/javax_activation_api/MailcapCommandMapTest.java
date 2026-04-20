/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.javax_activation_api;

import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.activation.DataContentHandler;
import javax.activation.DataSource;
import javax.activation.MailcapCommandMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MailcapCommandMapTest {
    @Test
    void createDataContentHandlerInstantiatesHandlersLoadedByTheContextClassLoader() {
        MailcapCommandMap commandMap = new MailcapCommandMap();
        commandMap.addMailcap(mailcapEntry("text/x-context-loader", TestDataContentHandler.class));

        DataContentHandler handler = commandMap.createDataContentHandler("text/x-context-loader");

        assertNotNull(handler);
        assertInstanceOf(TestDataContentHandler.class, handler);
    }

    @Test
    void createDataContentHandlerFallsBackToClassForNameWhenTheContextClassLoaderCannotLoadTheHandler() {
        MailcapCommandMap commandMap = new MailcapCommandMap();
        commandMap.addMailcap(mailcapEntry("text/x-class-for-name", TestDataContentHandler.class));

        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        RejectingClassLoader rejectingClassLoader = new RejectingClassLoader(
                originalContextClassLoader,
                Set.of(TestDataContentHandler.class.getName())
        );
        currentThread.setContextClassLoader(rejectingClassLoader);
        try {
            DataContentHandler handler = commandMap.createDataContentHandler("text/x-class-for-name");

            assertNotNull(handler);
            assertInstanceOf(TestDataContentHandler.class, handler);
            assertThat(rejectingClassLoader.getRejectedLoads())
                    .containsExactly(TestDataContentHandler.class.getName());
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static String mailcapEntry(String mimeType, Class<? extends DataContentHandler> handlerClass) {
        return mimeType + ";; x-java-content-handler=" + handlerClass.getName();
    }

    public static final class TestDataContentHandler implements DataContentHandler {
        public TestDataContentHandler() {
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[0];
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
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final Set<String> rejectedNames;
        private final List<String> rejectedLoads;

        private RejectingClassLoader(ClassLoader parent, Set<String> rejectedNames) {
            super(parent);
            this.rejectedNames = rejectedNames;
            this.rejectedLoads = new ArrayList<>();
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (rejectedNames.contains(name)) {
                rejectedLoads.add(name);
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }

        private List<String> getRejectedLoads() {
            return rejectedLoads;
        }
    }
}
