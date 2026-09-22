/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_integration.spring_integration_sftp;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.sshd.sftp.client.SftpClient.Attributes;
import org.apache.sshd.sftp.client.SftpClient.DirEntry;
import org.junit.jupiter.api.Test;

import org.springframework.integration.sftp.filters.SftpSimplePatternFileListFilter;
import org.springframework.integration.sftp.filters.SftpSystemMarkerFilePresentFileListFilter;

public class SftpSystemMarkerFilePresentFileListFilterTest {

    @Test
    void selectsOnlyRemoteFilesWithCompletionMarkers() {
        DirEntry completedFile = entry("ready.txt");
        DirEntry completionMarker = entry("ready.txt.complete");
        DirEntry incompleteFile = entry("pending.txt");
        SftpSystemMarkerFilePresentFileListFilter filter =
                new SftpSystemMarkerFilePresentFileListFilter(new SftpSimplePatternFileListFilter("*.txt"));

        assertThat(filter.filterFiles(new DirEntry[] {completedFile, completionMarker, incompleteFile}))
                .containsExactly(completedFile);
    }

    private static DirEntry entry(String filename) {
        return new DirEntry(filename, filename, new Attributes());
    }
}
