/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_docgen;

import io.vertx.docgen.BaseProcessor;
import io.vertx.docgen.DocGenProcessor;
import io.vertx.docgen.JavaDocGenProcessor;
import io.vertx.docgen.LanguageFilterPostProcessor;
import io.vertx.docgen.PostProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BaseProcessorTest {
    @Test
    void registersAndRetrievesAUsablePostProcessorOnBothProcessorImplementations() {
        for (BaseProcessor processor : List.of(new DocGenProcessor(), new JavaDocGenProcessor())) {
            PostProcessor filter = new LanguageFilterPostProcessor();
            assertSame(processor, processor.registerPostProcessor(filter));
            assertSame(filter, processor.getPostProcessor("language"));
            assertSame(filter, processor.getPostProcessor("LANGUAGE"));
            assertEquals("Java guide", processor.getPostProcessor("language")
                    .process("java", "Java guide", "java"));
            assertNull(processor.getPostProcessor("unregistered"));
        }
    }

    @Test
    void rejectsDuplicateNamesWithoutReplacingTheRegisteredProcessor() {
        BaseProcessor processor = new DocGenProcessor();
        PostProcessor filter = new LanguageFilterPostProcessor();
        processor.registerPostProcessor(filter);
        assertThrows(IllegalArgumentException.class,
                () -> processor.registerPostProcessor(new LanguageFilterPostProcessor()));
        assertSame(filter, processor.getPostProcessor("language"));
        assertEquals("", processor.getPostProcessor("language").process("ruby", "Java guide", "java"));
    }
}
