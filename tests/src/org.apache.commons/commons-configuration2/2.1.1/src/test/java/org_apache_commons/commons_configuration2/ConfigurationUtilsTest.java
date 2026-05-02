/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ConfigurationUtils;
import org.apache.commons.configuration2.ImmutableConfiguration;
import org.apache.commons.configuration2.builder.BasicBuilderParameters;
import org.apache.commons.configuration2.sync.ReadWriteSynchronizer;
import org.apache.commons.configuration2.sync.Synchronizer;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ConfigurationUtilsTest {
    @Test
    void clonesCloneableObjectsThroughUtilityMethod() {
        final BasicBuilderParameters parameters = new BasicBuilderParameters();
        parameters.setThrowExceptionOnMissing(true);

        final Object clone = ConfigurationUtils.cloneIfPossible(parameters);

        assertThat(clone).isInstanceOf(BasicBuilderParameters.class);
        assertThat(clone).isNotSameAs(parameters);
        assertThat(((BasicBuilderParameters) clone).getParameters()).containsEntry("throwExceptionOnMissing", true);
    }

    @Test
    void clonesSynchronizerWithPublicDefaultConstructor() {
        final ReadWriteSynchronizer synchronizer = new ReadWriteSynchronizer();

        final Synchronizer clone = ConfigurationUtils.cloneSynchronizer(synchronizer);

        assertThat(clone).isInstanceOf(ReadWriteSynchronizer.class);
        assertThat(clone).isNotSameAs(synchronizer);
        clone.beginRead();
        clone.endRead();
    }

    @Test
    void loadsClassWithThreadContextClassLoader() throws ClassNotFoundException {
        final Thread thread = Thread.currentThread();
        final ClassLoader originalClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(ConfigurationUtilsTest.class.getClassLoader());
        try {
            try {
                assertThat(ConfigurationUtils.loadClass(BaseConfiguration.class.getName()))
                        .isEqualTo(BaseConfiguration.class);
            } catch (Error error) {
                rethrowIfNotNativeImageDynamicClassLoadingError(error);
            }
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void fallsBackToConfigurationUtilsClassLoader() throws ClassNotFoundException {
        final Thread thread = Thread.currentThread();
        final ClassLoader originalClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(null);
        try {
            try {
                assertThat(ConfigurationUtils.loadClass(ConfigurationUtils.class.getName()))
                        .isEqualTo(ConfigurationUtils.class);
            } catch (Error error) {
                rethrowIfNotNativeImageDynamicClassLoadingError(error);
            }
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void createsUnmodifiableConfigurationProxy() {
        final BaseConfiguration configuration = new BaseConfiguration();
        configuration.addProperty("feature", "enabled");

        final ImmutableConfiguration immutableConfiguration = ConfigurationUtils
                .unmodifiableConfiguration(configuration);

        assertThat(immutableConfiguration).isNotInstanceOf(Configuration.class);
        assertThat(immutableConfiguration.getString("feature")).isEqualTo("enabled");
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
