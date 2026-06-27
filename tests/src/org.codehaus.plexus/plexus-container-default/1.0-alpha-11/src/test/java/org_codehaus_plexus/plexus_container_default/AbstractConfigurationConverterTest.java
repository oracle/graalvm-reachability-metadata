/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
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
        ClassRealm classRealm = new ClassWorld().newRealm(
            "abstract-configuration-converter-test",
            AbstractConfigurationConverterTest.class.getClassLoader()
        );
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("component");
        configuration.setAttribute("implementation", ArrayList.class.getName());

        Class implementation = converter.getImplementationClass(Object.class, configuration, classRealm);
        Object instance = converter.instantiate(
            InstantiableComponent.class.getName(),
            AbstractConfigurationConverterTest.class.getClassLoader()
        );

        assertSame(ArrayList.class, implementation);
        assertTrue(instance instanceof InstantiableComponent);
    }

    @Test
    public void fallsBackToStringForTextOnlyCollectionElements() throws Exception {
        ExposingConfigurationConverter converter = new ExposingConfigurationConverter();
        ClassRealm classRealm = new ClassWorld().newRealm(
            "abstract-configuration-converter-fallback-test",
            AbstractConfigurationConverterTest.class.getClassLoader()
        );
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("item");
        configuration.setValue("literal-value");

        Class implementation = converter.resolveImplementationClass(
            null,
            InstantiableComponent.class,
            configuration,
            classRealm
        );

        assertSame(String.class, implementation);
    }

    public static final class InstantiableComponent {
    }

    private static final class ExposingConfigurationConverter extends AbstractConfigurationConverter {
        private Class getImplementationClass(Class type, PlexusConfiguration configuration, ClassRealm classRealm)
            throws ComponentConfigurationException {
            return getClassForImplementationHint(type, configuration, classRealm);
        }

        private Class resolveImplementationClass(Class type, Class baseType, PlexusConfiguration configuration,
                                                 ClassRealm classRealm)
            throws ComponentConfigurationException {
            return getImplementationClass(type, baseType, configuration, classRealm);
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
                                        ExpressionEvaluator expressionEvaluator,
                                        ConfigurationListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
