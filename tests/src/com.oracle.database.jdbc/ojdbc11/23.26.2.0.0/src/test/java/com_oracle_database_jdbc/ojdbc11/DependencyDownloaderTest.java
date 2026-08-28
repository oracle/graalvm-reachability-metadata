/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import oracle.jdbc.downloadmanager.DependencyDownloader;
import oracle.jdbc.downloadmanager.DependencyInfo;
import org.junit.jupiter.api.Test;

public class DependencyDownloaderTest {
    @Test
    void createsACallerSelectedDownloader() throws Exception {
        DependencyDownloader downloader = DependencyDownloader.createInstance(EmptyDependencyDownloader.class);

        assertThat(downloader).isInstanceOf(EmptyDependencyDownloader.class);
        assertThat(downloader.listAvailableJDBCExtensions()).isEmpty();
    }

    public static final class EmptyDependencyDownloader implements DependencyDownloader {
        public EmptyDependencyDownloader() {}

        @Override
        public List<DependencyInfo> listAvailableJDBCExtensions() {
            return List.of();
        }

        @Override
        public Set<DependencyInfo> downloadDependencies(DependencyInfo dependency, String destination) {
            return Set.of();
        }

        @Override
        public Set<DependencyInfo> downloadDependencies(
                DependencyInfo dependency, String destination, boolean includeTransitiveDependencies) {
            return Set.of();
        }
    }
}
