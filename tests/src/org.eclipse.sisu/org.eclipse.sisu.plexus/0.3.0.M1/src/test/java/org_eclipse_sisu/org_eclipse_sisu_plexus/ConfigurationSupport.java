/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_plexus;

import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.converters.ConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.DefaultExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;

final class ConfigurationSupport {
    private ConfigurationSupport() {
    }

    static Object convert(ConfigurationConverter converter, PlexusConfiguration configuration, Class<?> type,
                    Class<?> enclosingType) throws ComponentConfigurationException {
        return converter.fromConfiguration(new DefaultConverterLookup(), configuration, type, enclosingType,
                ConfigurationSupport.class.getClassLoader(), new DefaultExpressionEvaluator(), null);
    }
}
