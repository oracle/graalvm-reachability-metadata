/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import oracle.sql.ConverterArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ConverterArchiveTest {
    @TempDir
    Path tempDirectory;

    @Test
    void storesAndReadsObjectsInSupportedArchiveFormats() throws Exception {
        ConverterArchive archive = new ConverterArchive();
        Path singleObjectArchive = Path.of("converter-archive-" + UUID.randomUUID() + ".zip");
        try {
            archive.insertSingleObj(singleObjectArchive.toString(), "first-value", "first");
            archive.insertSingleObj(singleObjectArchive.toString(), "second-value", "second");

            assertThat(archive.readObj(singleObjectArchive.toString(), "first")).isEqualTo("first-value");
            assertThat(archive.readObj(singleObjectArchive.toString(), "second")).isEqualTo("second-value");
        } finally {
            Files.deleteIfExists(singleObjectArchive);
            Files.deleteIfExists(Path.of("gsstemp.zip"));
        }

        Path streamedArchive = tempDirectory.resolve("streamed.zip");
        archive.openArchiveforInsert(streamedArchive.toString());
        archive.insertObj("streamed-value", "value");
        archive.closeArchiveforInsert();
        assertThat(archive.readObj(streamedArchive.toString(), "value")).isEqualTo("streamed-value");

        String serializedFile = "converter.ser";
        archive.insertObjtoFile(tempDirectory + File.separator, serializedFile, "file-value");
        assertThat(tempDirectory.resolve(serializedFile)).isRegularFile();
        assertThat(Files.size(tempDirectory.resolve(serializedFile))).isPositive();
    }

    @Test
    void returnsNullWhenAConverterResourceIsAbsent() {
        ConverterArchive archive = new ConverterArchive();

        assertThat(archive.readObj("/no-such-oracle-converter.ser")).isNull();
    }
}
