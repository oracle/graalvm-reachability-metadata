/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.conf.ProtocolConfiguration;
import org.jgroups.conf.XmlConfigurator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlConfiguratorTest {
    private static final String STACK_RESOURCE = "org_jgroups/jgroups/xml-configurator-stack.xml";

    @Test
    void loadsResourceThroughContextClassLoaderLookup() throws Throwable {
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(XmlConfiguratorTest.class.getClassLoader());
        try (InputStream input = invokeResourceLookup(STACK_RESOURCE)) {
            assertThat(input).isNotNull();

            XmlConfigurator configurator = XmlConfigurator.getInstance(input);

            assertThat(protocolNames(configurator)).containsExactly("UDP", "DISCARD");
        }
        finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void fallsBackToXmlConfiguratorClassLoaderWhenContextClassLoaderMissesResource() throws Throwable {
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader emptyClassLoader = new URLClassLoader(new URL[0], null)) {
            Thread.currentThread().setContextClassLoader(emptyClassLoader);
            try (InputStream input = invokeResourceLookup(STACK_RESOURCE)) {
                assertThat(input).isNotNull();

                XmlConfigurator configurator = XmlConfigurator.getInstance(input);

                assertThat(protocolNames(configurator)).containsExactly("UDP", "DISCARD");
            }
        }
        finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void mainLoadsConfigurationResourceWhenFileIsAbsent() throws Exception {
        PrintStream originalOut = System.out;
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            Thread.currentThread().setContextClassLoader(XmlConfiguratorTest.class.getClassLoader());

            XmlConfigurator.main(new String[] {"-file", STACK_RESOURCE});
        }
        finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
            System.setOut(originalOut);
        }

        String printedConfiguration = output.toString(StandardCharsets.UTF_8);
        assertThat(printedConfiguration).contains("UDP").contains("DISCARD");
    }

    private static InputStream invokeResourceLookup(String resourceName) throws Throwable {
        MethodHandle resourceLookup = MethodHandles.privateLookupIn(XmlConfigurator.class, MethodHandles.lookup())
            .findStatic(XmlConfigurator.class, "getAsInputStreamFromClassLoader",
                MethodType.methodType(InputStream.class, String.class));

        return (InputStream) resourceLookup.invoke(resourceName);
    }

    private static List<String> protocolNames(XmlConfigurator configurator) {
        return configurator.getProtocolStack().stream()
            .map(ProtocolConfiguration::getProtocolName)
            .toList();
    }
}
