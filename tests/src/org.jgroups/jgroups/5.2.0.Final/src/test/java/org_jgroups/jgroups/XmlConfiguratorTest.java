/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;

import org.jgroups.conf.XmlConfigurator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlConfiguratorTest {
    private static final String CONFIGURATION_RESOURCE = "org_jgroups/jgroups/xml-configurator-main-resource.xml";
    private static final String CONFIGURATION_XML = """
            <config>
                <UDP bind_port="0"/>
                <PING/>
            </config>
            """;

    @Test
    void mainLoadsXmlConfigurationFromContextClassLoaderResource() throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousLoader = currentThread.getContextClassLoader();
        PrintStream previousOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        currentThread.setContextClassLoader(new InMemoryResourceClassLoader(CONFIGURATION_RESOURCE, CONFIGURATION_XML));
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            XmlConfigurator.main(new String[] {"-file", CONFIGURATION_RESOURCE});
        } finally {
            System.setOut(previousOut);
            currentThread.setContextClassLoader(previousLoader);
        }

        String printedStack = output.toString(StandardCharsets.UTF_8);
        assertThat(printedStack).contains("UDP")
                .contains("bind_port=0")
                .contains("PING");
    }

    private static final class InMemoryResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] resourceBytes;

        private InMemoryResourceClassLoader(String resourceName, String resourceContent) {
            super(null);
            this.resourceName = resourceName;
            resourceBytes = resourceContent.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected URL findResource(String name) {
            if (!resourceName.equals(name)) {
                return null;
            }
            try {
                return new URL(null, "memory:" + name, new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL url) {
                        return new URLConnection(url) {
                            @Override
                            public void connect() {
                                connected = true;
                            }

                            @Override
                            public InputStream getInputStream() throws IOException {
                                return new ByteArrayInputStream(resourceBytes);
                            }
                        };
                    }
                });
            } catch (IOException e) {
                throw new IllegalStateException("Could not create in-memory URL for " + name, e);
            }
        }
    }
}
