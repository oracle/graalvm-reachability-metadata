/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the XZ compression subsystem by exporting and streaming a FASTA reference-sequence dataset.
 */
public class XzReferenceSequenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    public void importsSequenceLengthsAndGcCountsWithoutExpandingTheDatasetToDisk() throws IOException {
        Path reference = temporaryDirectory.resolve("reference.fasta.xz");
        Map<String, String> sequenceLines = new LinkedHashMap<>();
        sequenceLines.put("chr-west", "ACGT".repeat(20));
        sequenceLines.put("chr-east", "GGCCAT".repeat(10));
        int linesPerSequence = 1000;

        // A producer writes wrapped FASTA directly into the downloadable XZ file.
        try (XZCompressorOutputStream compressed = new XZCompressorOutputStream(Files.newOutputStream(reference), 3);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(compressed, StandardCharsets.US_ASCII))) {
            for (Map.Entry<String, String> sequence : sequenceLines.entrySet()) {
                writer.write(">" + sequence.getKey() + " reference assembly\n");
                for (int line = 0; line < linesPerSequence; line++) {
                    writer.write(sequence.getValue());
                    writer.write('\n');
                }
            }
        }

        Map<String, Integer> sequenceLengths = new LinkedHashMap<>();
        Map<String, Integer> gcCounts = new LinkedHashMap<>();
        int sequenceLineCount = 0;
        // Downloaded references are processed with a 64 MiB decompressor memory budget.
        try (XZCompressorInputStream decompressed = new XZCompressorInputStream(
                new BufferedInputStream(Files.newInputStream(reference)), false, 64 * 1024);
                BufferedReader reader = new BufferedReader(new InputStreamReader(decompressed, StandardCharsets.US_ASCII))) {
            String currentSequence = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(">")) {
                    currentSequence = line.substring(1).split(" ", 2)[0];
                    if (sequenceLengths.putIfAbsent(currentSequence, 0) != null) {
                        throw new IOException("Duplicate sequence: " + currentSequence);
                    }
                    gcCounts.put(currentSequence, 0);
                } else {
                    if (currentSequence == null) {
                        throw new IOException("Sequence data before FASTA header");
                    }
                    int gc = 0;
                    for (int base = 0; base < line.length(); base++) {
                        char nucleotide = line.charAt(base);
                        switch (nucleotide) {
                            case 'G', 'C' -> gc++;
                            case 'A', 'T' -> { }
                            default -> throw new IOException("Invalid nucleotide: " + nucleotide);
                        }
                    }
                    sequenceLengths.merge(currentSequence, line.length(), Integer::sum);
                    gcCounts.merge(currentSequence, gc, Integer::sum);
                    sequenceLineCount++;
                }
            }
            // Reaching EOF also verifies the XZ stream's integrity check and footer.
            assertEquals(-1, decompressed.read());
        }

        assertEquals(2000, sequenceLineCount);
        assertEquals(Map.of("chr-west", 80000, "chr-east", 60000), sequenceLengths);
        assertEquals(Map.of("chr-west", 40000, "chr-east", 40000), gcCounts);
    }
}
