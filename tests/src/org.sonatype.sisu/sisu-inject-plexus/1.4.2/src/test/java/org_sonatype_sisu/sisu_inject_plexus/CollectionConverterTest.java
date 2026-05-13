/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Vector;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.AbstractConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.composite.CollectionConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.DefaultPlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.junit.jupiter.api.Test;

public class CollectionConverterTest {
    @Test
    void instantiatesConcreteCollectionType() throws ComponentConfigurationException {
        final DefaultPlexusConfiguration configuration = new DefaultPlexusConfiguration("items");

        final Object value = new CollectionConverter().fromConfiguration(
                new DefaultConverterLookup(),
                configuration,
                Vector.class,
                Object.class,
                CollectionConverterTest.class.getClassLoader(),
                null,
                null);

        assertThat(value).isInstanceOf(Vector.class);
        assertThat((Vector<?>) value).isEmpty();
    }

    @Test
    void loadsFullyQualifiedChildElementClass() throws ComponentConfigurationException {
        final DefaultPlexusConfiguration configuration = new DefaultPlexusConfiguration("items");
        configuration.addChild(new DefaultPlexusConfiguration(FullyQualifiedElement.class.getName()));

        final Collection<String> values = convertWithElementConverter(
                configuration,
                ElementBase.class,
                new ElementMarkerConverter(FullyQualifiedElement.class, "fully-qualified"));

        assertThat(values).containsExactly("fully-qualified");
    }

    @Test
    void loadsPackageRelativeChildElementClass() throws ComponentConfigurationException {
        final DefaultPlexusConfiguration configuration = new DefaultPlexusConfiguration("items");
        configuration.addChild(new DefaultPlexusConfiguration("relative-element"));

        final Collection<String> values = convertWithElementConverter(
                configuration,
                RelativeElementBase.class,
                new ElementMarkerConverter(RelativeElement.class, "package-relative"));

        assertThat(values).containsExactly("package-relative");
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> convertWithElementConverter(final PlexusConfiguration configuration,
                                                                  final Class<?> baseType,
                                                                  final ElementMarkerConverter elementConverter)
        throws ComponentConfigurationException {
        final DefaultConverterLookup converterLookup = new DefaultConverterLookup();
        converterLookup.registerConverter(elementConverter);

        final Object value = new CollectionConverter().fromConfiguration(
                converterLookup,
                configuration,
                List.class,
                baseType,
                CollectionConverterTest.class.getClassLoader(),
                null,
                null);

        assertThat(value).isInstanceOf(Collection.class);
        return (Collection<String>) value;
    }

    public interface ElementBase {
    }

    public interface RelativeElementBase {
    }

    public static final class FullyQualifiedElement implements ElementBase {
    }

    private static final class ElementMarkerConverter extends AbstractConfigurationConverter {
        private final Class<?> elementType;

        private final String marker;

        private ElementMarkerConverter(final Class<?> elementType, final String marker) {
            this.elementType = elementType;
            this.marker = marker;
        }

        @Override
        public boolean canConvert(final Class type) {
            return elementType.equals(type);
        }

        @Override
        public Object fromConfiguration(final ConverterLookup converterLookup, final PlexusConfiguration configuration,
                                        final Class type, final Class baseType, final ClassLoader classLoader,
                                        final ExpressionEvaluator expressionEvaluator,
                                        final ConfigurationListener listener)
            throws ComponentConfigurationException {
            return marker;
        }
    }
}

final class RelativeElement implements CollectionConverterTest.RelativeElementBase {
}
