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

/**
 * Covers dynamic access in {@code javax.activation.MailcapCommandMap}.
 */
public final class MailcapCommandMapTest {

    @Test
    void createDataContentHandlerUsesContextClassLoaderWhenItCanLoadHandler() {
        MailcapCommandMap commandMap = new MailcapCommandMap();
        String mimeType = "text/x-mailcap-command-map-context-loader";
        String handlerClassName = RecordingDataContentHandler.class.getName();
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();

        commandMap.addMailcap(mimeType + "; ; x-java-content-handler=" + handlerClassName);

        currentThread.setContextClassLoader(RecordingDataContentHandler.class.getClassLoader());
        try {
            DataContentHandler dataContentHandler = commandMap.createDataContentHandler(mimeType);

            assertThat(dataContentHandler).isInstanceOf(RecordingDataContentHandler.class);
        }
        finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void createDataContentHandlerFallsBackToClassForNameWhenContextClassLoaderCannotLoadHandler() {
        MailcapCommandMap commandMap = new MailcapCommandMap();
        String mimeType = "text/x-mailcap-command-map";
        String handlerClassName = RecordingDataContentHandler.class.getName();
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        ClassLoader rejectingClassLoader = new RejectingClassLoader(handlerClassName, originalContextClassLoader);

        commandMap.addMailcap(mimeType + "; ; x-java-content-handler=" + handlerClassName);

        currentThread.setContextClassLoader(rejectingClassLoader);
        try {
            DataContentHandler dataContentHandler = commandMap.createDataContentHandler(mimeType);

            assertThat(dataContentHandler).isInstanceOf(RecordingDataContentHandler.class);
        }
        finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingClassLoader(String rejectedClassName, ClassLoader parent) {
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
}
