/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_plexus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.codehaus.plexus.component.configurator.converters.composite.ObjectWithFieldsConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.DefaultExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

public class CompositeBeanHelperTest {
    @Test
    void configuresSetterAndFieldProperties() throws Exception {
        Options options = new Options();
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
        configuration.addChild("label", "release");
        configuration.addChild("retries", "4");

        new ObjectWithFieldsConverter().processConfiguration(new DefaultConverterLookup(), options,
                getClass().getClassLoader(), configuration, new DefaultExpressionEvaluator());

        assertEquals("release", options.getLabel());
        assertEquals(1, options.getSetterCalls());
        assertEquals(4, options.getRetries());
    }

    @Test
    void appliesTextThroughDefaultSetter() throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("endpoint");
        configuration.setValue("https://example.test/releases");

        Endpoint endpoint = (Endpoint) ConfigurationSupport.convert(new ObjectWithFieldsConverter(), configuration,
                Endpoint.class, getClass());

        assertEquals("https://example.test/releases", endpoint.getAddress());
    }

    public static class Options {
        private String label;
        private int setterCalls;
        private int retries;

        public void setLabel(String label) {
            this.label = label;
            setterCalls++;
        }

        public String getLabel() {
            return label;
        }

        public int getSetterCalls() {
            return setterCalls;
        }

        public int getRetries() {
            return retries;
        }
    }

    public static class Endpoint {
        private String address;

        public void set(String address) {
            this.address = address;
        }

        public String getAddress() {
            return address;
        }
    }
}
