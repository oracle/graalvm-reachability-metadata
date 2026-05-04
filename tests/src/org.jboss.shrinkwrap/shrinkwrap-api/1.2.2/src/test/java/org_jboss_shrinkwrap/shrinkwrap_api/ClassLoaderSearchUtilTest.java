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
    void archivePathFactoryFindsImplementationThroughConfiguredClassLoader() {
        ArchivePath path = ArchivePaths.create("WEB-INF/classes");

        assertThat(path.get()).isEqualTo("/WEB-INF/classes");
        assertThat(path.getParent().get()).isEqualTo("/WEB-INF");
    }
}
