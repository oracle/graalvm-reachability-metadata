/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_resolver.maven_resolver_impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import org.eclipse.aether.internal.impl.DefaultTrackingFileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultTrackingFileManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void updatesAndReadsTrackingProperties() {
        DefaultTrackingFileManager trackingFileManager = new DefaultTrackingFileManager();
        Path trackingFile = tempDir.resolve("tracking/resolver.properties");

        Properties updatedProperties = trackingFileManager.update(trackingFile, Map.of("artifact", "resolved"));
        Properties readProperties = trackingFileManager.read(trackingFile);

        assertThat(updatedProperties).containsEntry("artifact", "resolved");
        assertThat(readProperties).containsEntry("artifact", "resolved");
    }
}
