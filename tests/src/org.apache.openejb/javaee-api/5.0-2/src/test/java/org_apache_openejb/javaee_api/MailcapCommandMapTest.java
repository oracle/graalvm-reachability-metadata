/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_openejb.javaee_api;

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
    public void constructorInitializesMailcapLookups() throws Exception {
        MailcapCommandMap commandMap = withContextClassLoader(getClass().getClassLoader(), MailcapCommandMap::new);

        assertThat(commandMap.getMimeTypes()).isNotNull();
    }

    @Test
    public void createDataContentHandlerInstantiatesHandlerLoadedByTheContextClassLoader() throws Exception {
        DataContentHandler handler = withContextClassLoader(new ClassLoader(getClass().getClassLoader()) {
        }, () -> {
            MailcapCommandMap commandMap = new MailcapCommandMap();
            commandMap.addMailcap(
                    "application/x-mailcap-command-map-test;; x-java-content-handler="
                            + TestDataContentHandler.class.getName());

            return commandMap.createDataContentHandler("application/x-mailcap-command-map-test");
        });

        assertThat(handler).isInstanceOf(TestDataContentHandler.class);
    }

    private static <T> T withContextClassLoader(ClassLoader classLoader, ThrowingSupplier<T> supplier) throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(classLoader);
        try {
            return supplier.get();
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    public static final class TestDataContentHandler implements DataContentHandler {
        public TestDataContentHandler() {
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
            outputStream.write(new byte[0]);
        }
    }
}
