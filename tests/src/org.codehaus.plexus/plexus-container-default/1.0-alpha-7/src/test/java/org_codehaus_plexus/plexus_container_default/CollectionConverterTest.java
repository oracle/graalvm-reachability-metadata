/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.converters.ConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.composite.CollectionConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CollectionConverterTest {
    @Test
    public void recognizesCollectionsButNotMaps() {
        CollectionConverter converter = new CollectionConverter();

        assertTrue(converter.canConvert(ArrayList.class));
        assertFalse(converter.canConvert(Map.class));
    }

    @Test
    public void createsConcreteCollectionAndResolvesChildrenByClassNameAndBasePackage() throws Exception {
        CollectionConverter converter = new CollectionConverter();
        DefaultConverterLookup converterLookup = new DefaultConverterLookup();
        converterLookup.registerConverter(new NamedElementConverter());
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("items");
        addChild(configuration, "java.lang.String", "loaded from fully qualified child element");
        addChild(configuration, "collectionConverterTest$NamedElement", "loaded from base package child element");

        Object value = converter.fromConfiguration(
            converterLookup,
            configuration,
            ArrayList.class,
            CollectionConverterTest.class,
            CollectionConverterTest.class.getClassLoader(),
            new LiteralExpressionEvaluator()
        );

        assertTrue(value instanceof ArrayList);
        Collection collection = (Collection) value;
        assertEquals(2, collection.size());
        assertTrue(collection.contains("loaded from fully qualified child element"));
        assertTrue(collection.contains(new NamedElement("loaded from base package child element")));
    }

    private static void addChild(XmlPlexusConfiguration parent, String name, String value) {
        XmlPlexusConfiguration child = new XmlPlexusConfiguration(name);
        child.setValue(value);
        parent.addChild(child);
    }

    public static final class NamedElement {
        private final String value;

        private NamedElement(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof NamedElement)) {
                return false;
            }
            NamedElement that = (NamedElement) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    private static final class NamedElementConverter implements ConfigurationConverter {
        @Override
        public boolean canConvert(Class type) {
            return NamedElement.class.equals(type);
        }

        @Override
        public Object fromConfiguration(ConverterLookup converterLookup, PlexusConfiguration configuration, Class type,
                                        Class baseType, ClassLoader classLoader,
                                        ExpressionEvaluator expressionEvaluator)
            throws ComponentConfigurationException {
            try {
                return new NamedElement((String) expressionEvaluator.evaluate(configuration.getValue(null)));
            } catch (ExpressionEvaluationException e) {
                throw new ComponentConfigurationException("Could not create named element", e);
            }
        }
    }

    private static final class LiteralExpressionEvaluator implements ExpressionEvaluator {
        @Override
        public Object evaluate(String expression) throws ExpressionEvaluationException {
            return expression;
        }

        @Override
        public File alignToBaseDirectory(File file) {
            return file;
        }
    }
}
