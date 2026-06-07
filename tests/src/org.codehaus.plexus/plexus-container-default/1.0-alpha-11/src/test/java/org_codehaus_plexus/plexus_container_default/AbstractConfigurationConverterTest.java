/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.AbstractConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractConfigurationConverterTest {
    @Test
    public void loadsImplementationHintsAndInstantiatesLoadedClasses() throws Exception {
        ExposingConfigurationConverter converter = new ExposingConfigurationConverter();
        ClassLoader classLoader = AbstractConfigurationConverterTest.class.getClassLoader();
        ClassRealm classRealm = testRealm("abstract-configuration-converter-test");
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("component");
        configuration.setAttribute("implementation", ArrayList.class.getName());

        Class implementation = converter.getImplementationClass(Object.class, configuration, classRealm);
        Object instance = converter.instantiate(InstantiableComponent.class.getName(), classLoader);

        assertSame(ArrayList.class, implementation);
        assertTrue(instance instanceof InstantiableComponent);
    }

    public static final class InstantiableComponent {
    }

    private static ClassRealm testRealm(String id) throws NoSuchRealmException {
        return new ClassWorld(id, AbstractConfigurationConverterTest.class.getClassLoader()).getRealm(id);
    }

    private static final class ExposingConfigurationConverter extends AbstractConfigurationConverter {
        private Class getImplementationClass(Class type, PlexusConfiguration configuration, ClassRealm classRealm)
            throws ComponentConfigurationException {
            return getClassForImplementationHint(type, configuration, classRealm);
        }

        private Object instantiate(String className, ClassLoader classLoader) throws ComponentConfigurationException {
            return instantiateObject(className, classLoader);
        }

        @Override
        public boolean canConvert(Class type) {
            return false;
        }

        @Override
        public Object fromConfiguration(ConverterLookup converterLookup, PlexusConfiguration configuration, Class type,
                                        Class baseType, ClassRealm classRealm,
                                        ExpressionEvaluator expressionEvaluator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object fromConfiguration(ConverterLookup converterLookup, PlexusConfiguration configuration, Class type,
                                        Class baseType, ClassRealm classRealm,
                                        ExpressionEvaluator expressionEvaluator, ConfigurationListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
