/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import com.google.inject.Guice;
import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Test;
import org.sonatype.guice.plexus.converters.PlexusXmlBeanConverter;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PlexusXmlBeanConverterTest {
    @Test
    void convertsTextUsingStringConstructor() {
        PlexusXmlBeanConverter converter = newConverter();
        StringBuilder value = (StringBuilder) converter.convert(TypeLiteral.get(StringBuilder.class), "  endpoint  ");
        assertEquals("endpoint", value.toString());
    }

    @Test
    void convertsXmlToTypedArray() {
        String[] values = (String[]) newConverter().convert(TypeLiteral.get(String[].class), """
                <endpoints>
                    <endpoint>east</endpoint>
                    <endpoint>west</endpoint>
                </endpoints>
                """);
        assertArrayEquals(new String[]{"east", "west"}, values);
    }

    @Test
    void selectsImplementationUsingContextClassLoader() {
        String xml = """
                <endpoint implementation="%s"><address>https://east.example</address></endpoint>
                """.formatted(ConfiguredEndpoint.class.getName());
        ConfiguredEndpoint endpoint = (ConfiguredEndpoint) newConverter().convert(TypeLiteral.get(Object.class), xml);
        assertEquals("https://east.example", endpoint.getAddress());
    }

    @Test
    void selectsImplementationUsingPeerClassLoader() {
        PlexusXmlBeanConverter converter = newConverter();
        String xml = """
                <endpoint implementation="%s"><address>https://west.example</address></endpoint>
                """.formatted(ConfiguredEndpoint.class.getName());
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            ConfiguredEndpoint endpoint = (ConfiguredEndpoint) converter.convert(TypeLiteral.get(ConfiguredEndpoint.class), xml);
            assertEquals("https://west.example", endpoint.getAddress());
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Test
    void selectsBootstrapImplementationWithoutContextClassLoader() {
        PlexusXmlBeanConverter converter = newConverter();
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            Properties properties = (Properties) converter.convert(TypeLiteral.get(Properties.class), """
                    <properties implementation="java.util.Properties">
                        <property><name>region</name><value>east</value></property>
                    </properties>
                    """);
            assertEquals("east", properties.getProperty("region"));
            assertEquals(1, properties.size());
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static PlexusXmlBeanConverter newConverter() {
        PlexusXmlBeanConverter converter = new PlexusXmlBeanConverter();
        Guice.createInjector().injectMembers(converter);
        return converter;
    }
}
