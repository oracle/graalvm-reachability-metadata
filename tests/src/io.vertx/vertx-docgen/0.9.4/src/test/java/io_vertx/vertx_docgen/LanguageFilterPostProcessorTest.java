/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_docgen;

import io.vertx.docgen.LanguageFilterPostProcessor;
import io.vertx.docgen.PostProcessor;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LanguageFilterPostProcessorTest {
    @Test
    void retainsContentForAnyMatchingLanguageIgnoringCase() {
        PostProcessor filter = new LanguageFilterPostProcessor();
        String content = "Use the Java client.\n";
        assertEquals("language", filter.getName());
        assertEquals(content, filter.process("java", content, "groovy", "JAVA"));
        assertEquals(content, filter.process("JAVA", content, "java"));
    }

    @Test
    void removesContentForOtherLanguages() {
        PostProcessor filter = new LanguageFilterPostProcessor();
        assertEquals("", filter.process("java", "Use the Ruby client.", "ruby", "javascript"));
    }

    @Test
    void filtersParsedDocumentBlocks() {
        String declaration = "[language, java, groovy]";
        Iterator<String> lines = List.of("----", "Client instructions", "----").iterator();
        PostProcessor filter = new LanguageFilterPostProcessor();
        String content = PostProcessor.getBlockContent(lines);
        String[] languages = PostProcessor.getProcessorAttributes(declaration);
        assertEquals(filter.getName(), PostProcessor.getProcessorName(declaration));
        assertEquals("Client instructions\n", filter.process("java", content, languages));
        assertEquals("", filter.process("ruby", content, languages));
    }

    @Test
    void requiresAtLeastOneLanguageArgument() {
        PostProcessor filter = new LanguageFilterPostProcessor();
        assertThrows(IllegalArgumentException.class, () -> filter.process("java", "Instructions"));
    }
}
