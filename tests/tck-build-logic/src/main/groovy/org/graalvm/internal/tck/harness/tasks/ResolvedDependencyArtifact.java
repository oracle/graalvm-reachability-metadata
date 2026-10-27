/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import java.nio.file.Path;

/**
 * One JAR from the tested library's resolved runtime dependency closure.
 * §FS-metadata
 */
record ResolvedDependencyArtifact(String coordinate, Path file) {
}
