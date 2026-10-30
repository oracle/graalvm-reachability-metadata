/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import oracle.sql.ConverterArchive;
import oracle.sql.converter.JdbcCharacterConverters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ConverterArchiveTest {
    @TempDir
    Path directory;

    @Test
    void updatesArchiveEntriesWithoutLosingOtherValues() throws Exception {
        ConverterArchive archive = new ConverterArchive();
        Path zip = Path.of("character-converters-" + UUID.randomUUID() + ".zip");
        try {
            archive.insertSingleObj(zip.toString(), "original", "one");
            archive.insertSingleObj(zip.toString(), "retained", "two");
            archive.insertSingleObj(zip.toString(), "replacement", "one");

            assertThat(archive.readObj(zip.toString(), "one")).isEqualTo("replacement");
            assertThat(archive.readObj(zip.toString(), "two")).isEqualTo("retained");
        } finally {
            Files.deleteIfExists(zip);
            Files.deleteIfExists(Path.of("gsstemp.zip"));
        }
    }

    @Test
    void writesStreamedArchivesAndIndividualSerializedFiles() throws Exception {
        ConverterArchive archive = new ConverterArchive();
        Path zip = directory.resolve("streamed.zip");
        archive.openArchiveforInsert(zip.toString());
        try {
            archive.insertObj("converter payload", "entry");
        } finally {
            archive.closeArchiveforInsert();
        }
        assertThat(archive.readObj(zip.toString(), "entry")).isEqualTo("converter payload");

        archive.insertObjtoFile(directory + File.separator, "mapping.ser", "standalone payload");
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(directory.resolve("mapping.ser")))) {
            assertThat(input.readObject()).isEqualTo("standalone payload");
        }
    }

    @Test
    void readsThePackagedAsciiCharacterConverter() throws Exception {
        Object stored = new ConverterArchive().readObj("/oracle/sql/converter_xcharset/lx20001.glb");

        assertThat(stored).isInstanceOf(JdbcCharacterConverters.class);
        JdbcCharacterConverters converter = (JdbcCharacterConverters) stored;
        assertThat(converter.getOracleId()).isEqualTo(1);
        assertThat(converter.toUnicodeString(new byte[] {65, 90}, 0, 2)).isEqualTo("AZ");
    }
}
