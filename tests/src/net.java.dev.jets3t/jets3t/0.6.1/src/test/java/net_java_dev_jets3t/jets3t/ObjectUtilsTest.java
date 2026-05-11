/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jets3t.service.Constants;
import org.jets3t.service.acl.AccessControlList;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.multithread.DownloadPackage;
import org.jets3t.service.utils.Mimetypes;
import org.jets3t.service.utils.ObjectUtils;
import org.jets3t.service.utils.ServiceUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectUtilsTest {
    @TempDir
    Path tempDir;

    @Test
    public void preparesLocalFileForUploadWithMetadataAndRepeatableContent() throws Exception {
        byte[] payload = "payload for object utils".getBytes(StandardCharsets.UTF_8);
        Path dataPath = tempDir.resolve("payload.unknown-extension");
        Files.write(dataPath, payload);

        S3Object object = ObjectUtils.createObjectForUpload("objects/payload", dataPath.toFile(), null, false);

        assertThat(object.getKey()).isEqualTo("objects/payload");
        assertThat(object.getAcl()).isSameAs(AccessControlList.REST_CANNED_PRIVATE);
        assertThat(object.getContentLength()).isEqualTo(payload.length);
        assertThat(object.getContentType()).isEqualTo(Mimetypes.MIMETYPE_OCTET_STREAM);
        assertThat(object.getMetadata(Constants.METADATA_JETS3T_LOCAL_FILE_DATE)).isNotNull();
        assertThat(object.getMd5HashAsBase64()).isEqualTo(ServiceUtils.toBase64(ServiceUtils.computeMD5Hash(payload)));

        try (InputStream inputStream = object.getDataInputStream()) {
            assertThat(inputStream.readAllBytes()).isEqualTo(payload);
        }
    }

    @Test
    public void createsDownloadPackagesForFilesButNotDirectoryMarkers() throws Exception {
        S3Object object = new S3Object("objects/report.txt");
        object.setContentType("text/plain");
        File targetFile = tempDir.resolve("downloads/report.txt").toFile();

        DownloadPackage downloadPackage = ObjectUtils.createPackageForDownload(
            object, targetFile, false, false, null);

        assertThat(downloadPackage.getObject()).isSameAs(object);
        assertThat(downloadPackage.getDataFile()).isEqualTo(targetFile);
        assertThat(downloadPackage.isSignedDownload()).isFalse();

        S3Object directoryMarker = new S3Object("objects/");
        directoryMarker.setContentType(Mimetypes.MIMETYPE_JETS3T_DIRECTORY);

        assertThat(ObjectUtils.createPackageForDownload(directoryMarker, targetFile, true, true, "password"))
            .isNull();
    }
}
