/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_activation.jakarta_activation;

import jakarta.activation.ActivationDataFlavor;
import jakarta.activation.DataContentHandler;
import jakarta.activation.DataSource;
import jakarta.activation.MailcapCommandMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class MailcapCommandMapTest {

    @Test
    void createDataContentHandlerLoadsHandlerWithClassLoader() {
        MailcapCommandMap commandMap = new MailcapCommandMap();
        commandMap.addMailcap(mailcapEntry("application/x-classloader-handler", ClassLoaderDataContentHandler.class));

        DataContentHandler dataContentHandler = commandMap.createDataContentHandler("application/x-classloader-handler");

        assertThat(dataContentHandler).isInstanceOf(ClassLoaderDataContentHandler.class);
    }

    @Test
    void createDataContentHandlerFallsBackToClassForName() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        String blockedClassName = FallbackDataContentHandler.class.getName();
        Thread.currentThread().setContextClassLoader(new RejectingClassLoader(originalClassLoader, blockedClassName));
        try {
            MailcapCommandMap commandMap = new MailcapCommandMap();
            commandMap.addMailcap(mailcapEntry("application/x-class-for-name-handler", FallbackDataContentHandler.class));

            DataContentHandler dataContentHandler = commandMap.createDataContentHandler("application/x-class-for-name-handler");

            assertThat(dataContentHandler).isInstanceOf(FallbackDataContentHandler.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static String mailcapEntry(String mimeType, Class<? extends DataContentHandler> dataContentHandlerClass) {
        return mimeType + "; ; x-java-content-handler=" + dataContentHandlerClass.getName();
    }

    public static final class ClassLoaderDataContentHandler implements DataContentHandler {

        @Override
        public ActivationDataFlavor[] getTransferDataFlavors() {
            return new ActivationDataFlavor[0];
        }

        @Override
        public Object getTransferData(ActivationDataFlavor dataFlavor, DataSource dataSource) {
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

    public static final class FallbackDataContentHandler implements DataContentHandler {

        @Override
        public ActivationDataFlavor[] getTransferDataFlavors() {
            return new ActivationDataFlavor[0];
        }

        @Override
        public Object getTransferData(ActivationDataFlavor dataFlavor, DataSource dataSource) {
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

        private final String blockedClassName;

        private RejectingClassLoader(ClassLoader parent, String blockedClassName) {
            super(parent);
            this.blockedClassName = blockedClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (blockedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
