/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.DuplicateRealmException;
import org.codehaus.plexus.component.configurator.AbstractComponentConfigurator;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.expression.DefaultExpressionEvaluator;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.DefaultPlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.junit.jupiter.api.Test;

public class AbstractComponentConfiguratorTest {
    @Test
    void invokesLegacyConfiguratorMethodWithListener() throws ComponentConfigurationException, DuplicateRealmException {
        LegacyConfiguratorWithListener configurator = new LegacyConfiguratorWithListener();
        TestComponent component = new TestComponent();
        PlexusConfiguration configuration = new DefaultPlexusConfiguration("component");
        ExpressionEvaluator evaluator = new DefaultExpressionEvaluator();
        ClassRealm realm = newRealm("with-listener");
        RecordingConfigurationListener listener = new RecordingConfigurationListener();

        configurator.configureComponent(component, configuration, evaluator, realm, listener);

        assertThat(configurator.component).isSameAs(component);
        assertThat(configurator.configuration).isSameAs(configuration);
        assertThat(configurator.expressionEvaluator).isSameAs(evaluator);
        assertThat(configurator.classRealm.getId()).isEqualTo("with-listener");
        assertThat(configurator.listener).isSameAs(listener);
    }

    @Test
    void fallsBackToLegacyConfiguratorMethodWithoutListener()
        throws ComponentConfigurationException, DuplicateRealmException {
        LegacyConfiguratorWithoutListener configurator = new LegacyConfiguratorWithoutListener();
        TestComponent component = new TestComponent();
        PlexusConfiguration configuration = new DefaultPlexusConfiguration("component");
        ExpressionEvaluator evaluator = new DefaultExpressionEvaluator();
        ClassRealm realm = newRealm("without-listener");
        RecordingConfigurationListener listener = new RecordingConfigurationListener();

        configurator.configureComponent(component, configuration, evaluator, realm, listener);

        assertThat(configurator.component).isSameAs(component);
        assertThat(configurator.configuration).isSameAs(configuration);
        assertThat(configurator.expressionEvaluator).isSameAs(evaluator);
        assertThat(configurator.classRealm.getId()).isEqualTo("without-listener");
    }

    private static ClassRealm newRealm(final String id) throws DuplicateRealmException {
        ClassWorld world = new ClassWorld();
        return world.newRealm(id, AbstractComponentConfiguratorTest.class.getClassLoader());
    }

    public static final class LegacyConfiguratorWithListener extends AbstractComponentConfigurator {
        private Object component;
        private PlexusConfiguration configuration;
        private ExpressionEvaluator expressionEvaluator;
        private org.codehaus.classworlds.ClassRealm classRealm;
        private ConfigurationListener listener;

        public void configureComponent(final Object component, final PlexusConfiguration configuration,
                                       final ExpressionEvaluator expressionEvaluator,
                                       final org.codehaus.classworlds.ClassRealm classRealm,
                                       final ConfigurationListener listener) {
            this.component = component;
            this.configuration = configuration;
            this.expressionEvaluator = expressionEvaluator;
            this.classRealm = classRealm;
            this.listener = listener;
        }
    }

    public static final class LegacyConfiguratorWithoutListener extends AbstractComponentConfigurator {
        private Object component;
        private PlexusConfiguration configuration;
        private ExpressionEvaluator expressionEvaluator;
        private org.codehaus.classworlds.ClassRealm classRealm;

        public void configureComponent(final Object component, final PlexusConfiguration configuration,
                                       final ExpressionEvaluator expressionEvaluator,
                                       final org.codehaus.classworlds.ClassRealm classRealm) {
            this.component = component;
            this.configuration = configuration;
            this.expressionEvaluator = expressionEvaluator;
            this.classRealm = classRealm;
        }
    }

    public static final class TestComponent {
    }

    public static final class RecordingConfigurationListener implements ConfigurationListener {
        @Override
        public void notifyFieldChangeUsingSetter(final String fieldName, final Object value, final Object target) {
        }

        @Override
        public void notifyFieldChangeUsingReflection(final String fieldName, final Object value, final Object target) {
        }
    }
}
