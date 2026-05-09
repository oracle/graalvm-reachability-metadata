/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jets3t.service.Constants;
import org.jets3t.service.Jets3tProperties;
import org.jets3t.service.model.S3Bucket;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.utils.FileComparer;
import org.jets3t.service.utils.FileComparerResults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class FileComparerTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    public void buildsFileMapUsingPrefixesDirectoryEntriesAndIgnorePatterns() throws Exception {
        Path rootDirectory = temporaryDirectory.resolve("root");
        Files.createDirectories(rootDirectory.resolve("nested"));
        writeFile(rootDirectory.resolve("visible.txt"), "visible");
        writeFile(rootDirectory.resolve("ignored.tmp"), "ignored");
        writeFile(rootDirectory.resolve("digest.md5"), "ignored-md5");
        writeFile(rootDirectory.resolve("nested").resolve("child.txt"), "child");
        writeFile(
            rootDirectory.resolve(Constants.JETS3T_IGNORE_FILENAME),
            "*.tmp\n.jets3t-ignore\n");

        Jets3tProperties properties = propertiesWith("filecomparer.skip-upload-of-md5-files=true\n");
        FileComparer comparer = FileComparer.getInstance(properties);

        Map<String, File> fileMap = comparer.buildFileMap(rootDirectory.toFile(), "backup", true);

        assertThat(fileMap.keySet()).containsExactlyInAnyOrder(
            "backup/visible.txt",
            "backup/nested",
            "backup/nested/child.txt");
        assertThat(fileMap.get("backup/visible.txt"))
            .isEqualTo(rootDirectory.resolve("visible.txt").toFile());
        assertThat(((File) fileMap.get("backup/nested")).isDirectory()).isTrue();
    }

    @Test
    public void classifiesSynchronisedClientOnlyAndServerOnlyObjects() throws Exception {
        File sameFile = writeFile(temporaryDirectory.resolve("same.txt"), "same contents").toFile();
        File clientOnlyFile = writeFile(temporaryDirectory.resolve("client-only.txt"), "local").toFile();
        Map<String, File> filesMap = new LinkedHashMap<>();
        filesMap.put("same.txt", sameFile);
        filesMap.put("client-only.txt", clientOnlyFile);

        S3Bucket bucket = new S3Bucket("coverage-bucket");
        S3Object sameObject = new S3Object(bucket, "same.txt", "same contents");
        S3Object serverOnlyObject = new S3Object(bucket, "server-only.txt", "remote");
        Map<String, S3Object> s3ObjectsMap = new LinkedHashMap<>();
        s3ObjectsMap.put("same.txt", sameObject);
        s3ObjectsMap.put("server-only.txt", serverOnlyObject);

        FileComparer comparer = FileComparer.getInstance(propertiesWith(""));

        FileComparerResults results = comparer.buildDiscrepancyLists(filesMap, s3ObjectsMap);

        assertThat(results.alreadySynchronisedKeys).containsExactly("same.txt");
        assertThat(results.onlyOnClientKeys).containsExactly("client-only.txt");
        assertThat(results.onlyOnServerKeys).containsExactly("server-only.txt");
        assertThat(results.updatedOnClientKeys).isEmpty();
        assertThat(results.updatedOnServerKeys).isEmpty();
    }

    private static Jets3tProperties propertiesWith(String contents) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(
            contents.getBytes(StandardCharsets.ISO_8859_1));
        return Jets3tProperties.getInstance(inputStream, "file-comparer-test-" + System.nanoTime());
    }

    private static Path writeFile(Path file, String contents) throws IOException {
        Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
