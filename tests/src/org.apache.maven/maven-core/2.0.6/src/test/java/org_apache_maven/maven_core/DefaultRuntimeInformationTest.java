/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.execution.DefaultRuntimeInformation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DefaultRuntimeInformationTest {
    @Test
    public void initializeReadsBundledMavenCoreProperties() throws Exception {
        DefaultRuntimeInformation runtimeInformation = new DefaultRuntimeInformation();

        runtimeInformation.initialize();

        ArtifactVersion applicationVersion = runtimeInformation.getApplicationVersion();
        assertNotNull(applicationVersion);
        assertFalse(applicationVersion.toString().isBlank());
    }
}
