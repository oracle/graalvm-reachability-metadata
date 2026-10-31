/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import io.github.classgraph.ClassGraph;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VersionFinderTest {
    @Test
    void readsPublishedArtifactVersion() {
        assertTrue(ClassGraph.getVersion().matches("[0-9]+\\.[0-9]+.*"));
    }
}
