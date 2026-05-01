/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_shrinkwrap.shrinkwrap_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.junit.jupiter.api.Test;

public class ClassLoaderSearchUtilTest {
    @Test
    void archivePathsResolveImplementationThroughLibraryClassLoader() {
        ArchivePath path = ArchivePaths.create("content/index.html");

        assertThat(path.get()).isEqualTo("/content/index.html");
        assertThat(path.getParent().get()).isEqualTo("/content");
    }
}
