/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.conf.ProtocolConfiguration;
import org.jgroups.conf.XmlConfigurator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlConfiguratorTest {
    private static final String RESOURCE_NAME = "in-memory-jgroups-stack.xml";
    private static final String STACK_XML = """
            <config>
                <SHARED_LOOPBACK />
                <SHARED_LOOPBACK_PING />
                <pbcast.GMS join_timeout="10000" />
            </config>
            """;

    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void parsesXmlConfigurationFromInputStream() throws Exception {
        XmlConfigurator configurator = XmlConfigurator.getInstance(xmlInputStream());
        List<ProtocolConfiguration> protocols = configurator.getProtocolStack();

        assertThat(protocols).extracting(ProtocolConfiguration::getProtocolName)
                .containsExactly("SHARED_LOOPBACK", "SHARED_LOOPBACK_PING", "pbcast.GMS");
        assertThat(protocols.get(2).getProperties()).containsEntry("join_timeout", "10000");
        assertThat(configurator.getProtocolStackString(true))
                .contains("<config>")
                .contains("<SHARED_LOOPBACK />")
                .contains("<pbcast.GMS join_timeout=\"10000\"/>");
    }

    @Test
    void mainReadsXmlConfigurationFromContextClassLoaderResourceFallback() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        PrintStream originalOut = System.out;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        try {
            Thread.currentThread().setContextClassLoader(new InMemoryXmlResourceClassLoader(originalClassLoader));
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));

            XmlConfigurator.main(new String[] {"-file", RESOURCE_NAME});
        }
        finally {
            System.setOut(originalOut);
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(stdout.toString(StandardCharsets.UTF_8))
                .contains("SHARED_LOOPBACK")
                .contains("SHARED_LOOPBACK_PING")
                .contains("pbcast.GMS(join_timeout=10000)");
    }

    private static InputStream xmlInputStream() {
        return new ByteArrayInputStream(STACK_XML.getBytes(StandardCharsets.UTF_8));
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    private static final class InMemoryXmlResourceClassLoader extends ClassLoader {
        private InMemoryXmlResourceClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (RESOURCE_NAME.equals(name)) {
                return xmlInputStream();
            }
            return super.getResourceAsStream(name);
        }
    }
}
