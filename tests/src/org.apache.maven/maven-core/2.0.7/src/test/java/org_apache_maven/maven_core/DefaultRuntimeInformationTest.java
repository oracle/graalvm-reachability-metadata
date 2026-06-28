/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.execution.DefaultRuntimeInformation;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultRuntimeInformationTest {
    @Test
    void initializesApplicationVersionFromBundledMavenProperties() throws InitializationException {
        DefaultRuntimeInformation runtimeInformation = new DefaultRuntimeInformation();

        runtimeInformation.initialize();

        ArtifactVersion applicationVersion = runtimeInformation.getApplicationVersion();
        assertThat(applicationVersion).isNotNull();
        assertThat(applicationVersion.toString()).isNotBlank();
        assertThat(applicationVersion.getMajorVersion()).isGreaterThanOrEqualTo(0);
    }
}
