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
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class OutputModelWalkerTest {
    @TempDir
    Path outputDirectory;

    @Test
    void rendersRecognizerAndTreeTraversalSourcesFromGrammar() throws IOException {
        Grammar grammar = GrammarTestSupport.process("""
                grammar Records;
                document: pair+ EOF;
                pair: key=ID '=' value=INT;
                ID: [a-z]+;
                INT: [0-9]+;
                WS: [ \\t\\r\\n]+ -> skip;
                """);
        CodeGenerator generator = new CodeGenerator(grammar);

        String parser = GrammarTestSupport.render(generator, generator.generateParser(), outputDirectory);
        assertTrue(parser.contains("class RecordsParser extends Parser"));
        assertTrue(parser.contains("class DocumentContext"));
        assertTrue(parser.contains("class PairContext"));
        assertTrue(parser.contains("Token key;"));
        assertTrue(parser.contains("Token value;"));
        assertTrue(parser.contains("match(ID)"));
        assertTrue(parser.contains("match(INT)"));

        CodeGenerator lexerGenerator = new CodeGenerator(grammar.getImplicitLexer());
        String lexer = GrammarTestSupport.render(lexerGenerator, lexerGenerator.generateLexer(), outputDirectory);
        assertTrue(lexer.contains("class RecordsLexer extends Lexer"));
        assertTrue(lexer.contains("ID"));
        assertTrue(lexer.contains("INT"));

        String listener = GrammarTestSupport.render(generator, generator.generateListener(), outputDirectory);
        assertTrue(listener.contains("interface RecordsListener extends ParseTreeListener"));
        assertTrue(listener.contains("enterPair(@NotNull RecordsParser.PairContext ctx)"));
        assertTrue(listener.contains("exitDocument(@NotNull RecordsParser.DocumentContext ctx)"));
        String baseListener = GrammarTestSupport.render(generator, generator.generateBaseListener(), outputDirectory);
        assertTrue(baseListener.contains("class RecordsBaseListener"));

        String visitor = GrammarTestSupport.render(generator, generator.generateVisitor(), outputDirectory);
        assertTrue(visitor.contains("interface RecordsVisitor<Result> extends ParseTreeVisitor<Result>"));
        assertTrue(visitor.contains("visitPair(@NotNull RecordsParser.PairContext ctx)"));
        String baseVisitor = GrammarTestSupport.render(generator, generator.generateBaseVisitor(), outputDirectory);
        assertTrue(baseVisitor.contains("class RecordsBaseVisitor<Result>"));
        assertTrue(baseVisitor.contains("return visitChildren(ctx);"));
        assertEquals(0, grammar.tool.getNumErrors());
    }
}
