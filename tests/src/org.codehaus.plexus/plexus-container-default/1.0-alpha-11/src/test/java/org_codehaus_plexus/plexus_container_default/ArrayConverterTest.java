/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.component.configurator.converters.composite.ArrayConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ArrayConverterTest {
    @Test
    public void convertsArrayElementsResolvedByClassNameBasePackageAndComponentType() throws Exception {
        ExposingArrayConverter converter = new ExposingArrayConverter();
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("items");
        addChild(configuration, "java.lang.String", "resolved from fully qualified element name");
        addChild(configuration, "string", "resolved from base package");
        addChild(configuration, "unknown", "resolved from array component type");

        Object value = converter.fromConfiguration(
            new DefaultConverterLookup(),
            configuration,
            String[].class,
            String.class,
            ArrayConverterTest.class.getClassLoader(),
            new LiteralExpressionEvaluator()
        );

        assertArrayEquals(
            new String[] {
                "resolved from fully qualified element name",
                "resolved from base package",
                "resolved from array component type"
            },
            (String[]) value
        );
    }

    @Test
    public void createsDefaultListCollection() {
        ExposingArrayConverter converter = new ExposingArrayConverter();

        Collection collection = converter.defaultCollection(ArrayList.class);

        assertTrue(collection instanceof ArrayList);
        assertTrue(collection.isEmpty());
    }

    private static void addChild(XmlPlexusConfiguration parent, String name, String value) {
        XmlPlexusConfiguration child = new XmlPlexusConfiguration(name);
        child.setValue(value);
        parent.addChild(child);
    }

    private static final class ExposingArrayConverter extends ArrayConverter {
        private Collection defaultCollection(Class collectionType) {
            return getDefaultCollection(collectionType);
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
