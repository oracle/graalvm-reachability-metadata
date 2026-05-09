/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jets3t.service.model.S3Object;
import org.jets3t.service.multithread.DownloadPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class DownloadPackageTest {
    @TempDir
    Path tempDir;

    @Test
    public void writesObjectDownloadToOutputFileAndSupportsAppending() throws Exception {
        S3Object object = new S3Object("objects/report.txt");
        File outputFile = tempDir.resolve("downloads/report.txt").toFile();
        DownloadPackage downloadPackage = new DownloadPackage(object, outputFile);

        assertThat(downloadPackage.getObject()).isSameAs(object);
        assertThat(downloadPackage.getDataFile()).isEqualTo(outputFile);
        assertThat(downloadPackage.isSignedDownload()).isFalse();
        assertThat(downloadPackage.isAppendToFile()).isFalse();

        try (OutputStream outputStream = downloadPackage.getOutputStream()) {
            outputStream.write("first".getBytes("UTF-8"));
        }

        downloadPackage.setAppendToFile(true);
        assertThat(downloadPackage.isAppendToFile()).isTrue();

        try (OutputStream outputStream = downloadPackage.getOutputStream()) {
            outputStream.write("second".getBytes("UTF-8"));
        }

        assertThat(Files.readString(outputFile.toPath())).isEqualTo("firstsecond");
    }

    @Test
    public void managesSignedDownloadUrlIndependentlyOfObjectDownloads() {
        File outputFile = tempDir.resolve("signed/object.txt").toFile();
        DownloadPackage downloadPackage = new DownloadPackage(
            "https://example.invalid/original", outputFile, false, null);

        assertThat(downloadPackage.getObject()).isNull();
        assertThat(downloadPackage.getDataFile()).isEqualTo(outputFile);
        assertThat(downloadPackage.getSignedUrl()).isEqualTo("https://example.invalid/original");
        assertThat(downloadPackage.isSignedDownload()).isTrue();

        downloadPackage.setSignedUrl("https://example.invalid/replacement");

        assertThat(downloadPackage.getSignedUrl()).isEqualTo("https://example.invalid/replacement");
        assertThat(downloadPackage.isSignedDownload()).isTrue();
    }
}
