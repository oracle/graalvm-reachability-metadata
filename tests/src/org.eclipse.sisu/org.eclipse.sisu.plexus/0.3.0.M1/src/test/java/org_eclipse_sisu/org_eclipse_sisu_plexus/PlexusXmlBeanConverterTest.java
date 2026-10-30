/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_plexus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.File;

import com.google.inject.Guice;
import com.google.inject.TypeLiteral;
import org.eclipse.sisu.plexus.PlexusBeanConverter;
import org.eclipse.sisu.plexus.PlexusXmlBeanConverter;
import org.junit.jupiter.api.Test;

public class PlexusXmlBeanConverterTest {
    private final PlexusBeanConverter converter = Guice.createInjector().getInstance(PlexusXmlBeanConverter.class);

    @Test
    void convertsXmlSequenceToTypedArray() {
        String[] values = (String[]) converter.convert(TypeLiteral.get(String[].class), """
                <values><value>alpha</value><value>beta</value></values>
                """);

        assertArrayEquals(new String[]{"alpha", "beta"}, values);
    }

    @Test
    void selectsNamedImplementationAndConfiguresItsProperties() {
        String xml = """
                <settings implementation="%s"><label>from XML</label></settings>
                """.formatted(NamedSettings.class.getName());

        Settings settings = (Settings) converter.convert(TypeLiteral.get(Settings.class), xml);

        assertInstanceOf(NamedSettings.class, settings);
        assertEquals("from XML", settings.getLabel());
    }

    @Test
    void resolvesImplementationFromPeerWithoutThreadContextLoader() {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            String xml = """
                    <settings implementation="%s"><label>peer</label></settings>
                    """.formatted(NamedSettings.class.getName());

            Settings settings = (Settings) converter.convert(TypeLiteral.get(Settings.class), xml);

            assertInstanceOf(NamedSettings.class, settings);
            assertEquals("peer", settings.getLabel());
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    @Test
    void resolvesBootstrapImplementationWithoutThreadContextLoader() {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            CharSequence text = (CharSequence) converter.convert(TypeLiteral.get(CharSequence.class), """
                    <text implementation="java.lang.StringBuilder">converted</text>
                    """);

            assertInstanceOf(StringBuilder.class, text);
            assertEquals("converted", text.toString());
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    @Test
    void constructsValueFromText() {
        File file = (File) converter.convert(TypeLiteral.get(File.class), "report.txt");

        assertEquals("report.txt", file.getPath());
    }

    public static class Settings {
        private String label;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    public static class NamedSettings extends Settings {
    }
}
