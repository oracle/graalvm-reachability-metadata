/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tunnelvisionlabs.antlr4;

import java.io.IOException;
import java.nio.file.Path;

import org.antlr.v4.codegen.CodeGenerator;
import org.antlr.v4.tool.Grammar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class ActionTranslatorTest {
    @TempDir
    Path outputDirectory;

    @Test
    void translatesAllTokenPropertiesInParserActions() throws IOException {
        String source = generate("""
                grammar TokenActions;
                entry: n=INT {
                    String text = $n.text;
                    int type = $n.type;
                    int line = $n.line;
                    int column = $n.pos;
                    int channel = $n.channel;
                    int index = $n.index;
                    int number = $n.int;
                };
                INT: [0-9]+;
                """);

        assertTrue(source.contains(".n.getText()"));
        assertTrue(source.contains(".n.getType()"));
        assertTrue(source.contains(".n.getLine()"));
        assertTrue(source.contains(".n.getCharPositionInLine()"));
        assertTrue(source.contains(".n.getChannel()"));
        assertTrue(source.contains(".n.getTokenIndex()"));
        assertTrue(source.contains("Integer.valueOf("));
        assertFalse(source.contains("$n."));
    }

    @Test
    void translatesCurrentRulePropertiesInParserActions() throws IOException {
        String source = generate("""
                grammar CurrentRuleActions;
                entry: INT {
                    Object first = $start;
                    Object last = $stop;
                    String text = $text;
                    Object context = $ctx;
                    Object parser = $parser;
                };
                INT: [0-9]+;
                """);

        assertTrue(source.contains("Object first = _localctx.start;"));
        assertTrue(source.contains("Object last = _localctx.stop;"));
        assertTrue(source.contains("_input.getText(_localctx.start, _input.LT(-1))"));
        assertTrue(source.contains("Object context = _localctx;"));
        assertTrue(source.contains("Object parser = this;"));
        assertFalse(source.contains("$start"));
    }

    @Test
    void translatesReferencedRulePropertiesInParserActions() throws IOException {
        String source = generate("""
                grammar ReferencedRuleActions;
                entry: r=number {
                    Object first = $r.start;
                    Object last = $r.stop;
                    String text = $r.text;
                    Object context = $r.ctx;
                    Object parser = $r.parser;
                };
                number: INT;
                INT: [0-9]+;
                """);

        assertTrue(source.contains(".r.start"));
        assertTrue(source.contains(".r.stop"));
        assertTrue(source.contains("_input.getText("));
        assertTrue(source.contains("Object context = _localctx.r;"));
        assertTrue(source.contains("Object parser = this;"));
        assertFalse(source.contains("$r."));
    }

    private String generate(String grammarSource) throws IOException {
        Grammar grammar = GrammarTestSupport.process(grammarSource);
        CodeGenerator generator = new CodeGenerator(grammar);
        String source = GrammarTestSupport.render(generator, generator.generateParser(), outputDirectory);
        assertEquals(0, grammar.tool.getNumErrors(), "Action translation must succeed");
        return source;
    }
}
