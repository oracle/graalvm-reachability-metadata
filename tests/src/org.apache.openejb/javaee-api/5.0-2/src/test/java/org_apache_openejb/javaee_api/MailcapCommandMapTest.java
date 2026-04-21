/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_openejb.javaee_api;

import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.io.OutputStream;

import javax.activation.DataContentHandler;
import javax.activation.DataSource;
import javax.activation.MailcapCommandMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MailcapCommandMapTest {
    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();

    @AfterEach
    void restoreThreadContextClassLoader() {
        currentThread.setContextClassLoader(originalContextClassLoader);
    }

    @Test
    void constructorLoadsClasspathMailcapResources() {
        currentThread.setContextClassLoader(MailcapCommandMapTest.class.getClassLoader());

        MailcapCommandMap commandMap = new MailcapCommandMap();

        assertThat(commandMap.getMimeTypes()).contains("text/x-mailcap-resource");
        assertThat(commandMap.createDataContentHandler("text/x-mailcap-resource"))
                .isInstanceOf(TestDataContentHandler.class);
    }

    @Test
    void createDataContentHandlerInstantiatesHandlersAddedProgrammatically() {
        MailcapCommandMap commandMap = new MailcapCommandMap();
        commandMap.addMailcap(mailcapEntry("text/x-programmatic-handler", TestDataContentHandler.class));

        assertThat(commandMap.createDataContentHandler("text/x-programmatic-handler"))
                .isInstanceOf(TestDataContentHandler.class);
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
}
