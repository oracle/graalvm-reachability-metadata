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

import org.codehaus.plexus.component.configurator.converters.composite.ObjectWithFieldsConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.DefaultExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

public class AbstractConfigurationConverterTest {
    @Test
    void instantiatesConfiguredImplementation() throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("destination");
        configuration.setAttribute("implementation", DirectoryDestination.class.getName());
        configuration.addChild("directory", "output");

        Destination destination = (Destination) ConfigurationSupport.convert(new ObjectWithFieldsConverter(),
                configuration, Destination.class, getClass());

        assertInstanceOf(DirectoryDestination.class, destination);
        assertEquals("output", destination.directory());
    }

    @Test
    void resolvesArrayImplementationHintForBeanProperty() throws Exception {
        SearchOptions options = new SearchOptions();
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
        XmlPlexusConfiguration paths = new XmlPlexusConfiguration("paths");
        paths.setAttribute("implementation", "java.lang.String[]");
        paths.addChild("path", "src");
        paths.addChild("path", "resources");
        configuration.addChild(paths);

        new ObjectWithFieldsConverter().processConfiguration(new DefaultConverterLookup(), options,
                getClass().getClassLoader(), configuration, new DefaultExpressionEvaluator());

        assertArrayEquals(new String[]{"src", "resources"}, options.getPaths());
    }

    public interface Destination {
        String directory();
    }

    public static class DirectoryDestination implements Destination {
        private String directory;

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        @Override
        public String directory() {
            return directory;
        }
    }

    public static class SearchOptions {
        private String[] paths;

        public void setPaths(String[] paths) {
            this.paths = paths;
        }

        public String[] getPaths() {
            return paths;
        }
    }
}
