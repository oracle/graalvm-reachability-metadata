/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_docgen;

import io.vertx.docgen.PostProcessor;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PostProcessorTest {
    @Test
    void parsesBlockDeclarationsAndTrimsTheirAttributes() {
        String declaration = "[language, java, javascript ]";
        assertTrue(PostProcessor.isBlockDeclaration(declaration));
        assertEquals("language", PostProcessor.getProcessorName(declaration));
        assertArrayEquals(new String[]{"java", "javascript"},
                PostProcessor.getProcessorAttributes(declaration));
        assertEquals("source", PostProcessor.getProcessorName("[ source ]"));
        assertArrayEquals(new String[0], PostProcessor.getProcessorAttributes("[source]"));
        assertFalse(PostProcessor.isBlockDeclaration("ordinary document text"));
        assertFalse(PostProcessor.isBlockDeclaration("[]"));
    }

    @Test
    void readsDelimitedContentWithoutConsumingTheFollowingParagraph() {
        Iterator<String> lines = List.of("----", "  First line  ", "Second line", "----", "After").iterator();
        assertEquals("First line\nSecond line\n", PostProcessor.getBlockContent(lines));
        assertEquals("After", lines.next());
        assertFalse(lines.hasNext());
    }

    @Test
    void restoresEscapedDelimitersForNestedSourceBlocks() {
        Iterator<String> lines = List.of("----", "[source,java]", "\\----", "int count = 3;",
                "\\----", "----", "After").iterator();
        assertEquals("[source,java]\n----\nint count = 3;\n----\n", PostProcessor.getBlockContent(lines));
        assertEquals("After", lines.next());
    }

    @Test
    void readsAnUndelimitedBlockAsOneLine() {
        Iterator<String> lines = List.of("  Java only  ", "Next paragraph").iterator();
        assertEquals("Java only", PostProcessor.getBlockContent(lines));
        assertEquals("Next paragraph", lines.next());
    }

    @Test
    void readsAnEmptyDelimitedBlock() {
        Iterator<String> lines = List.of("----", "----", "After").iterator();
        assertEquals("", PostProcessor.getBlockContent(lines));
        assertEquals("After", lines.next());
    }
}
