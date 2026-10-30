/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_plexus;

import org.codehaus.classworlds.ClassRealm;
import org.codehaus.plexus.component.configurator.AbstractComponentConfigurator;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.composite.ObjectWithFieldsConverter;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;

public final class LegacyConfigurators {
    private LegacyConfigurators() {
    }

    public static class Basic extends AbstractComponentConfigurator {
        public void configureComponent(Object component, PlexusConfiguration configuration,
                        ExpressionEvaluator evaluator, ClassRealm realm) throws ComponentConfigurationException {
            new ObjectWithFieldsConverter().processConfiguration(converterLookup, component, realm.getClassLoader(),
                    configuration, evaluator);
        }
    }

    public static class WithListener extends Basic {
        public void configureComponent(Object component, PlexusConfiguration configuration,
                        ExpressionEvaluator evaluator, ClassRealm realm, ConfigurationListener listener)
                        throws ComponentConfigurationException {
            new ObjectWithFieldsConverter().processConfiguration(converterLookup, component, realm.getClassLoader(),
                    configuration, evaluator, listener);
        }
    }
}
