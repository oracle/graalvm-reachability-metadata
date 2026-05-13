/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.AbstractConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.DefaultPlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.junit.jupiter.api.Test;

public class AbstractConfigurationConverterTest {
    @Test
    void loadsClassFromImplementationHint() throws ComponentConfigurationException {
        final ConverterHarness converter = new ConverterHarness();
        final DefaultPlexusConfiguration configuration = new DefaultPlexusConfiguration("component");
        configuration.setAttribute("implementation", ImplementationHintComponent.class.getName());

        final Class<?> implementation = converter.getImplementation(
                ComponentRole.class, configuration, AbstractConfigurationConverterTest.class.getClassLoader());

        assertThat(implementation).isEqualTo(ImplementationHintComponent.class);
    }

    @Test
    void loadsAndInstantiatesClassByName() throws ComponentConfigurationException {
        final ConverterHarness converter = new ConverterHarness();

        final Object value = converter.instantiate(
                InstantiatedComponent.class.getName(), AbstractConfigurationConverterTest.class.getClassLoader());

        assertThat(value).isInstanceOf(InstantiatedComponent.class);
    }

    public interface ComponentRole {
    }

    public static class ImplementationHintComponent implements ComponentRole {
        public ImplementationHintComponent() {
        }
    }

    public static class InstantiatedComponent {
        public InstantiatedComponent() {
        }
    }

    private static final class ConverterHarness extends AbstractConfigurationConverter {
        Class<?> getImplementation(final Class<?> type, final PlexusConfiguration configuration,
                                   final ClassLoader classLoader) throws ComponentConfigurationException {
            return getClassForImplementationHint(type, configuration, classLoader);
        }

        Object instantiate(final String className, final ClassLoader classLoader)
            throws ComponentConfigurationException {
            return instantiateObject(className, classLoader);
        }

        @Override
        public boolean canConvert(final Class type) {
            return false;
        }

        @Override
        public Object fromConfiguration(final ConverterLookup converterLookup, final PlexusConfiguration configuration,
                                        final Class type, final Class baseType, final ClassLoader classLoader,
                                        final ExpressionEvaluator expressionEvaluator,
                                        final ConfigurationListener listener)
            throws ComponentConfigurationException {
            throw new ComponentConfigurationException("ConverterHarness does not convert configurations");
        }
    }
}
