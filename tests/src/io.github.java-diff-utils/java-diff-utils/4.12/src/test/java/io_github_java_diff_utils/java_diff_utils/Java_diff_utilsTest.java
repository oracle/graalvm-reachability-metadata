/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_java_diff_utils.java_diff_utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.algorithm.DiffAlgorithmListener;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import com.github.difflib.patch.VerifyChunk;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRow.Tag;
import com.github.difflib.text.DiffRowGenerator;
import com.github.difflib.unifieddiff.UnifiedDiff;
import com.github.difflib.unifieddiff.UnifiedDiffFile;
import com.github.difflib.unifieddiff.UnifiedDiffReader;
import com.github.difflib.unifieddiff.UnifiedDiffWriter;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class Java_diff_utilsTest {
    @Test
    void appliesAndRestoresPatchContainingInsertDeleteAndChangeDeltas() throws Exception {
        List<String> original = List.of("alpha", "remove me", "kept after deletion", "old value", "omega");
        List<String> revised = List.of("intro", "alpha", "kept after deletion", "new value", "omega", "outro");

        Patch<String> patch = DiffUtils.diff(original, revised);
        List<String> patched = DiffUtils.patch(original, patch);
        List<String> restored = DiffUtils.unpatch(revised, patch);

        assertThat(patched).containsExactlyElementsOf(revised);
        assertThat(restored).containsExactlyElementsOf(original);
        assertThat(patch.applyTo(original)).containsExactlyElementsOf(revised);
        assertThat(patch.restore(revised)).containsExactlyElementsOf(original);
        assertThat(patch.getDeltas())
                .extracting(AbstractDelta::getType)
                .contains(DeltaType.INSERT, DeltaType.CHANGE, DeltaType.DELETE);
    }

    @Test
    void identifiesStableDeltaShapesAndChunkVerificationResults() throws Exception {
        Patch<String> insertPatch = DiffUtils.diff(List.of("a", "c"), List.of("a", "b", "c"));
        Patch<String> deletePatch = DiffUtils.diff(List.of("a", "b", "c"), List.of("a", "c"));
        Patch<String> changePatch = DiffUtils.diff(List.of("a", "b", "c"), List.of("a", "x", "c"));

        AbstractDelta<String> insert = singleDelta(insertPatch);
        AbstractDelta<String> delete = singleDelta(deletePatch);
        AbstractDelta<String> change = singleDelta(changePatch);

        assertThat(insert.getType()).isEqualTo(DeltaType.INSERT);
        assertThat(insert.getSource().getPosition()).isEqualTo(1);
        assertThat(insert.getSource().getLines()).isEmpty();
        assertThat(insert.getTarget().getLines()).containsExactly("b");

        assertThat(delete.getType()).isEqualTo(DeltaType.DELETE);
        assertThat(delete.getSource().getLines()).containsExactly("b");
        assertThat(delete.getTarget().getLines()).isEmpty();

        assertThat(change.getType()).isEqualTo(DeltaType.CHANGE);
        assertThat(change.getSource().verifyChunk(List.of("a", "b", "c"))).isEqualTo(VerifyChunk.OK);
        assertThat(change.getSource().verifyChunk(List.of("a", "different", "c")))
                .isEqualTo(VerifyChunk.CONTENT_DOES_NOT_MATCH_TARGET);
        assertThat(new Chunk<>(5, List.of("missing")).verifyChunk(List.of("a")))
                .isEqualTo(VerifyChunk.POSITION_OUT_OF_TARGET);
        assertThat(change.withChunks(new Chunk<>(1, List.of("b")), new Chunk<>(1, List.of("x"))))
                .isEqualTo(change);
    }

    @Test
    void rejectsPatchesWhenSourceContentDoesNotMatch() {
        Patch<String> patch = DiffUtils.diff(List.of("first", "second"), List.of("first", "replacement"));

        assertThatThrownBy(() -> patch.applyTo(List.of("first", "unexpected")))
                .isInstanceOf(PatchFailedException.class);
    }

    @Test
    void appliesPatchFuzzilyWhenSourceChunkIsShifted() throws Exception {
        List<String> original = List.of("header", "old setting", "footer");
        List<String> revised = List.of("header", "new setting", "footer");
        List<String> shiftedTarget = List.of("banner", "header", "old setting", "footer", "trailer");
        Patch<String> patch = DiffUtils.diff(original, revised);

        List<String> patched = patch.applyFuzzy(shiftedTarget, 0);

        assertThat(patched).containsExactly("banner", "header", "new setting", "footer", "trailer");
        assertThat(shiftedTarget).containsExactly("banner", "header", "old setting", "footer", "trailer");
    }

    @Test
    void supportsCustomEqualityAndProgressListener() {
        List<String> original = List.of("Alpha", "Beta", "Gamma");
        List<String> revised = List.of("alpha", "BETA", "gamma");
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger steps = new AtomicInteger();
        AtomicInteger ends = new AtomicInteger();
        DiffAlgorithmListener listener = new DiffAlgorithmListener() {
            @Override
            public void diffStart() {
                starts.incrementAndGet();
            }

            @Override
            public void diffStep(int value, int max) {
                steps.incrementAndGet();
            }

            @Override
            public void diffEnd() {
                ends.incrementAndGet();
            }
        };

        Patch<String> caseInsensitivePatch = DiffUtils.diff(
                original,
                revised,
                (left, right) -> left.toLowerCase(Locale.ROOT).equals(right.toLowerCase(Locale.ROOT)));
        Patch<String> observedPatch = DiffUtils.diff(original, revised, listener);

        assertThat(caseInsensitivePatch.getDeltas()).isEmpty();
        assertThat(observedPatch.getDeltas()).extracting(AbstractDelta::getType).containsOnly(DeltaType.CHANGE);
        assertThat(starts.get()).isEqualTo(1);
        assertThat(steps.get()).isPositive();
        assertThat(ends.get()).isEqualTo(1);
    }

    @Test
    void createsInlineStringDiffThatCanBeAppliedAndRestored() throws Exception {
        String original = "abc";
        String revised = "axc";

        Patch<String> inlinePatch = DiffUtils.diffInline(original, revised);

        assertThat(inlinePatch.getDeltas()).extracting(AbstractDelta::getType).contains(DeltaType.CHANGE);
        assertThat(DiffUtils.patch(List.of("a", "b", "c"), inlinePatch)).containsExactly("a", "x", "c");
        assertThat(DiffUtils.unpatch(List.of("a", "x", "c"), inlinePatch)).containsExactly("a", "b", "c");
    }

    @Test
    void generatesHumanReadableDiffRowsWithInlineMarkersAndWhitespaceEqualizer() {
        DiffRowGenerator inlineGenerator = DiffRowGenerator.create()
                .showInlineDiffs(true)
                .inlineDiffByWord(true)
                .oldTag(open -> open ? "<old>" : "</old>")
                .newTag(open -> open ? "<new>" : "</new>")
                .build();
        DiffRowGenerator whitespaceIgnoringGenerator = DiffRowGenerator.create()
                .equalizer((left, right) -> left.replace(" ", "").equals(right.replace(" ", "")))
                .reportLinesUnchanged(true)
                .build();

        List<DiffRow> rows = inlineGenerator.generateDiffRows(
                List.of("same", "The quick brown fox", "removed"),
                List.of("same", "The quick red fox", "added"));
        List<DiffRow> whitespaceRows = whitespaceIgnoringGenerator.generateDiffRows(
                List.of("value = 1"),
                List.of("value=1"));

        assertThat(rows).extracting(DiffRow::getTag).containsExactly(Tag.EQUAL, Tag.CHANGE, Tag.CHANGE);
        assertThat(rows.get(1).getOldLine()).contains("<old>", "brown", "</old>");
        assertThat(rows.get(1).getNewLine()).contains("<new>", "red", "</new>");
        assertThat(whitespaceRows).singleElement().satisfies(row -> {
            assertThat(row.getTag()).isEqualTo(Tag.EQUAL);
            assertThat(row.getOldLine()).isEqualTo("value = 1");
            assertThat(row.getNewLine()).isEqualTo("value = 1");
        });
    }

    @Test
    void roundTripsUnifiedDiffThroughUtilityParserAndReader() throws Exception {
        List<String> original = List.of("line one", "line two", "line three", "line four");
        List<String> revised = List.of("line zero", "line one", "line 2", "line three", "line four");
        Patch<String> patch = DiffUtils.diff(original, revised);

        List<String> unifiedLines = UnifiedDiffUtils.generateUnifiedDiff(
                "original.txt", "revised.txt", original, patch, 2);
        Patch<String> parsedPatch = UnifiedDiffUtils.parseUnifiedDiff(unifiedLines);
        UnifiedDiff parsedDocument = UnifiedDiffReader.parseUnifiedDiff(new ByteArrayInputStream(
                (String.join("\n", unifiedLines) + "\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(unifiedLines).first().isEqualTo("--- original.txt");
        assertThat(unifiedLines).contains("+++ revised.txt");
        assertThat(unifiedLines).anyMatch(line -> line.startsWith("@@"));
        assertThat(DiffUtils.patch(original, parsedPatch)).containsExactlyElementsOf(revised);
        assertThat(parsedDocument.getFiles()).hasSize(1);
        UnifiedDiffFile file = parsedDocument.getFiles().get(0);
        assertThat(file.getFromFile()).isEqualTo("original.txt");
        assertThat(file.getToFile()).isEqualTo("revised.txt");
        assertThat(DiffUtils.patch(original, file.getPatch())).containsExactlyElementsOf(revised);
    }

    @Test
    void appliesFilePatchSelectedFromParsedUnifiedDiffDocument() throws Exception {
        List<String> originalReadme = List.of("# Title", "old body");
        List<String> revisedReadme = List.of("# Title", "new body", "footer");
        List<String> originalNotes = List.of("keep", "remove");
        List<String> revisedNotes = List.of("keep");

        List<String> readmeDiff = UnifiedDiffUtils.generateUnifiedDiff(
                "README.md", "README.md", originalReadme, DiffUtils.diff(originalReadme, revisedReadme), 1);
        List<String> notesDiff = UnifiedDiffUtils.generateUnifiedDiff(
                "notes.txt", "notes.txt", originalNotes, DiffUtils.diff(originalNotes, revisedNotes), 1);
        List<String> combinedDiff = new ArrayList<>();
        combinedDiff.addAll(readmeDiff);
        combinedDiff.addAll(notesDiff);
        UnifiedDiff unifiedDiff = UnifiedDiffReader.parseUnifiedDiff(new ByteArrayInputStream(
                (String.join("\n", combinedDiff) + "\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(unifiedDiff.getFiles()).hasSize(2);
        assertThat(unifiedDiff.applyPatchTo("README.md"::equals, originalReadme))
                .containsExactlyElementsOf(revisedReadme);
        assertThat(unifiedDiff.applyPatchTo("notes.txt"::equals, originalNotes))
                .containsExactlyElementsOf(revisedNotes);
    }

    @Test
    void writesUnifiedDiffDocumentsWithMetadataAndContextLines() throws Exception {
        List<String> original = List.of("one", "two", "three", "four", "five");
        List<String> revised = List.of("one", "two updated", "three", "four", "five", "six");
        UnifiedDiffFile file = UnifiedDiffFile.from(
                "src/example.txt", "src/example.txt", DiffUtils.diff(original, revised));
        file.setDiffCommand("diff --git a/src/example.txt b/src/example.txt");
        file.setIndex("1111111..2222222 100644");
        UnifiedDiff unifiedDiff = UnifiedDiff.from("repository header", "repository tail", file);
        StringWriter writer = new StringWriter();

        UnifiedDiffWriter.write(unifiedDiff, path -> {
            assertThat(path).isEqualTo("src/example.txt");
            return original;
        }, writer, 1);
        String writtenDiff = writer.toString();
        List<String> writtenLines = writtenDiff.lines().toList();
        assertThat(writtenLines).startsWith(
                "repository header",
                "diff --git a/src/example.txt b/src/example.txt",
                "index 1111111..2222222 100644",
                "--- src/example.txt",
                "+++ src/example.txt");
        assertThat(writtenLines).contains("-two", "+two updated", "+six", "--", "repository tail");
    }

    private static AbstractDelta<String> singleDelta(Patch<String> patch) {
        assertThat(patch.getDeltas()).hasSize(1);
        return patch.getDeltas().get(0);
    }
}
