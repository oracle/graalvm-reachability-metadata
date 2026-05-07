/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.surefire.api.booter.BaseProviderFactory;
import org.apache.maven.surefire.api.provider.ProviderParameters;
import org.apache.maven.surefire.api.testset.TestArtifactInfo;
import org.apache.maven.surefire.booter.SurefireReflector;
import org.junit.jupiter.api.Test;

public class SurefireReflectorTest {

    @Test
    public void constructorLoadsProviderApiTypesAndConfiguresProviderFactory() {
        final ClassLoader classLoader = SurefireReflectorTest.class.getClassLoader();
        final SurefireReflector reflector = new SurefireReflector(classLoader);
        final TestArtifactInfo testArtifactInfo = new TestArtifactInfo("provider-version", "tests");

        final BaseProviderFactory providerFactory = (BaseProviderFactory) reflector.createBooterConfiguration(
                classLoader,
                true);
        reflector.setTestArtifactInfoAware(providerFactory, testArtifactInfo);
        reflector.setSkipAfterFailureCount(providerFactory, 3);
        reflector.setSystemExitTimeout(providerFactory, 30);
        final Object provider = reflector.instantiateProvider(RecordingProvider.class.getName(), providerFactory);

        assertThat(providerFactory.isInsideFork()).isTrue();
        assertThat(providerFactory.getTestArtifactInfo().getVersion()).isEqualTo("provider-version");
        assertThat(providerFactory.getTestArtifactInfo().getClassifier()).isEqualTo("tests");
        assertThat(providerFactory.getSkipAfterFailureCount()).isEqualTo(3);
        assertThat(providerFactory.getSystemExitTimeout()).isEqualTo(30);
        assertThat(provider)
                .isInstanceOfSatisfying(RecordingProvider.class, recordingProvider ->
                        assertThat(recordingProvider.providerParameters()).isSameAs(providerFactory));
    }

    public static final class RecordingProvider {
        private final ProviderParameters providerParameters;

        public RecordingProvider(final ProviderParameters providerParameters) {
            this.providerParameters = providerParameters;
        }

        public ProviderParameters providerParameters() {
            return providerParameters;
        }
    }
}
