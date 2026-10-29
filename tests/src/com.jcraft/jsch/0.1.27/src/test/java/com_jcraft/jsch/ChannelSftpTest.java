/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ChannelSftpTest {
    private static SftpServer server;

    private Session session;
    private ChannelSftp sftp;

    @BeforeAll
    static void startServer() throws Exception {
        server = SftpServer.start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @BeforeEach
    void openChannel() throws Exception {
        session = server.connect();
        sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect();
    }

    @AfterEach
    void closeChannel() {
        if (sftp != null) {
            sftp.disconnect();
        }
        if (session != null) {
            session.disconnect();
        }
    }

    @Test
    void managesFilesAndDirectoriesRelativeToTheWorkingDirectory() throws Exception {
        sftp.cd(SftpServer.UPLOAD_DIRECTORY);
        assertThat(sftp.pwd()).isEqualTo(SftpServer.UPLOAD_DIRECTORY);
        sftp.mkdir("reports");
        sftp.put(stream("quarterly numbers\n"), "reports/draft.txt");
        sftp.rename("reports/draft.txt", "reports/final.txt");

        assertThat(fileNames("reports")).contains("final.txt").doesNotContain("draft.txt");
        assertThat(sftp.stat("reports").isDir()).isTrue();
        SftpATTRS attributes = sftp.stat("reports/final.txt");
        assertThat(attributes.isDir()).isFalse();
        assertThat(attributes.getSize()).isEqualTo("quarterly numbers\n".length());
        ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
        sftp.get("reports/final.txt", downloaded);
        assertThat(downloaded.toString(StandardCharsets.US_ASCII)).isEqualTo("quarterly numbers\n");

        sftp.rm("reports/final.txt");
        sftp.rmdir("reports");
        assertThat(fileNames(".")).doesNotContain("reports");
    }

    @Test
    void changesPermissionsAndModificationTime() throws Exception {
        String path = SftpServer.UPLOAD_DIRECTORY + "/attributes.txt";
        int modificationTime = 1_000_000_000;
        sftp.put(stream("attributes"), path);
        sftp.chmod(0640, path);
        sftp.setMtime(path, modificationTime);

        SftpATTRS attributes = sftp.stat(path);
        assertThat(attributes.getPermissionsString()).isEqualTo("-rw-r-----");
        assertThat(attributes.getMTime()).isEqualTo(modificationTime);
        sftp.rm(path);
    }

    @Test
    void appendsToAFileAndReportsDownloadProgress() throws Exception {
        String path = SftpServer.UPLOAD_DIRECTORY + "/journal.txt";
        String journal = "first entry\nsecond entry\n";
        sftp.put(stream("first entry\n"), path);
        sftp.put(stream("second entry\n"), path, ChannelSftp.APPEND);

        RecordingMonitor monitor = new RecordingMonitor();
        ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
        sftp.get(path, downloaded, monitor);
        assertThat(downloaded.toString(StandardCharsets.US_ASCII)).isEqualTo(journal);
        assertThat(monitor.events).containsExactly(
                "init " + SftpProgressMonitor.GET + " " + path + " " + journal.length(),
                "count " + journal.length(),
                "end");
        sftp.rm(path);
    }

    @Test
    void streamsUploadsAndDownloads() throws Exception {
        String path = SftpServer.UPLOAD_DIRECTORY + "/streamed.txt";
        try (OutputStream upload = sftp.put(path)) {
            upload.write("streamed through the channel".getBytes(StandardCharsets.US_ASCII));
        }
        try (InputStream download = sftp.get(path)) {
            assertThat(new String(download.readAllBytes(), StandardCharsets.US_ASCII))
                    .isEqualTo("streamed through the channel");
        }
        sftp.rm(path);
    }

    @Test
    void reportsAMissingFileWithTheNoSuchFileStatus() {
        assertThatThrownBy(() -> sftp.stat(SftpServer.UPLOAD_DIRECTORY + "/missing.txt"))
                .isInstanceOfSatisfying(SftpException.class,
                        exception -> assertThat(exception.id).isEqualTo(ChannelSftp.SSH_FX_NO_SUCH_FILE));
    }

    private List<String> fileNames(String directory) throws SftpException {
        List<String> names = new ArrayList<>();
        for (Object entry : sftp.ls(directory)) {
            names.add(((ChannelSftp.LsEntry) entry).getFilename());
        }
        return names;
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII));
    }

    private static final class RecordingMonitor implements SftpProgressMonitor {
        private final List<String> events = new ArrayList<>();
        private long transferred;

        @Override
        public void init(int operation, String source, String destination, long max) {
            events.add("init " + operation + " " + source + " " + max);
        }

        @Override
        public boolean count(long count) {
            transferred += count;
            return true;
        }

        @Override
        public void end() {
            events.add("count " + transferred);
            events.add("end");
        }
    }
}
