/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the standalone LZMA compression subsystem by distributing an offline spelling dictionary.
 */
public class LzmaSpellingDictionaryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    public void importsCompressedWordsForSpellingChecksAndAutocomplete() throws IOException {
        List<String> publishedWords = List.of(
                "appliance", "application", "apply", "apricot", "archive", "backup",
                "caf\u00e9", "caffeine", "compress", "compression", "dictionary", "offline");
        Path download = temporaryDirectory.resolve("offline-dictionary.txt.lzma");

        // The publisher streams UTF-8 words into the standalone .lzma format, not an XZ container.
        try (LZMACompressorOutputStream compressed = new LZMACompressorOutputStream(Files.newOutputStream(download));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(compressed, StandardCharsets.UTF_8))) {
            for (String word : publishedWords) {
                writer.write(word);
                writer.write('\n');
            }
        }

        NavigableSet<String> dictionary = new TreeSet<>();
        int importedWords = 0;
        // The offline client limits decompressor memory to 64 MiB while indexing the downloaded words.
        try (LZMACompressorInputStream decompressed = new LZMACompressorInputStream(
                new BufferedInputStream(Files.newInputStream(download)), 64 * 1024);
                BufferedReader reader = new BufferedReader(new InputStreamReader(decompressed, StandardCharsets.UTF_8))) {
            String word;
            while ((word = reader.readLine()) != null) {
                if (word.isBlank() || !dictionary.add(word)) {
                    throw new IOException("Blank or duplicate dictionary entry: " + word);
                }
                importedWords++;
            }
            assertEquals(-1, decompressed.read());
        }

        assertEquals(publishedWords.size(), importedWords);
        assertEquals(new TreeSet<>(publishedWords), dictionary);
        assertTrue(dictionary.contains("caf\u00e9"));
        assertFalse(dictionary.contains("applcation"));
        assertEquals(List.of("appliance", "application", "apply"),
                dictionary.tailSet("appl", true).stream().takeWhile(word -> word.startsWith("appl")).toList());
        assertEquals(List.of("compress", "compression"),
                dictionary.tailSet("comp", true).stream().takeWhile(word -> word.startsWith("comp")).toList());
        assertTrue(dictionary.tailSet("zz", true).isEmpty());
    }
}
