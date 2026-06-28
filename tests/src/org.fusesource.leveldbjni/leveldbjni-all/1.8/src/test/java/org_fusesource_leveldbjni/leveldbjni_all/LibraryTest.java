/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_fusesource_leveldbjni.leveldbjni_all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fusesource.leveldbjni.JniDBFactory.asString;
import static org.fusesource.leveldbjni.JniDBFactory.bytes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.fusesource.leveldbjni.JniDBFactory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LibraryTest {
    @Test
    public void openDatabaseLoadsBundledNativeLibraryFromClasspath(@TempDir Path tempDirectory) throws IOException {
        File databaseDirectory = tempDirectory.resolve("leveldb").toFile();
        Options options = new Options().createIfMissing(true);

        try (DB database = JniDBFactory.factory.open(databaseDirectory, options)) {
            database.put(bytes("greeting"), bytes("hello leveldb"));

            assertThat(asString(database.get(bytes("greeting")))).isEqualTo("hello leveldb");
        }
    }
}
