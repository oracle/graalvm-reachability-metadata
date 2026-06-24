/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;

import org.apache.maven.surefire.api.booter.BaseProviderFactory;
import org.apache.maven.surefire.api.testset.TestArtifactInfo;
import org.apache.maven.surefire.booter.SurefireReflector;
import org.junit.jupiter.api.Test;

public class SurefireReflectorInnerClassLoaderProxyTest {

    @Test
    public void reflectorDispatchesConfigurationThroughSupportedProviderSetters() {
        final ClassLoader classLoader = getClass().getClassLoader();
        final SurefireReflector reflector = new SurefireReflector(classLoader);
        final BaseProviderFactory providerFactory = new BaseProviderFactory(false);
        final Map<String, String> providerProperties = Collections.singletonMap("tc.0", getClass().getName());
        final TestArtifactInfo artifactInfo = new TestArtifactInfo("provider-version", "tests");

        reflector.setTestClassLoaderAware(providerFactory, classLoader);
        reflector.setProviderPropertiesAware(providerFactory, providerProperties);
        reflector.setTestArtifactInfoAware(providerFactory, artifactInfo);

        assertThat(providerFactory.getTestClassLoader()).isSameAs(classLoader);
        assertThat(providerFactory.getProviderProperties()).isEqualTo(providerProperties);
        assertThat(providerFactory.getTestArtifactInfo().getVersion()).isEqualTo(artifactInfo.getVersion());
        assertThat(providerFactory.getTestArtifactInfo().getClassifier()).isEqualTo(artifactInfo.getClassifier());
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static final class GreetingDelegate implements GreetingService {
        @Override
        public String greet(final String name) {
            return "Hello, " + name;
        }
    }
}
