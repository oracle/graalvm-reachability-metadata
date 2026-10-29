/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tunnelvisionlabs.antlr4;

import org.antlr.v4.codegen.CodeGenerator;
import org.antlr.v4.tool.Grammar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class OutputModelWalkerTest {
    @Test
    void rendersRecognizerAndTreeTraversalSourcesFromGrammar() {
        Grammar grammar = GrammarTestSupport.process("""
                grammar Records;
                document: pair+ EOF;
                pair: key=ID '=' value=INT;
                ID: [a-z]+;
                INT: [0-9]+;
                WS: [ \\t\\r\\n]+ -> skip;
                """);
        CodeGenerator generator = new CodeGenerator(grammar);

        String parser = generator.generateParser().render();
        assertTrue(parser.contains("class RecordsParser extends Parser"));
        assertTrue(parser.contains("class DocumentContext"));
        assertTrue(parser.contains("class PairContext"));
        assertTrue(parser.contains("Token key;"));
        assertTrue(parser.contains("Token value;"));
        assertTrue(parser.contains("match(ID)"));
        assertTrue(parser.contains("match(INT)"));

        String lexer = new CodeGenerator(grammar.getImplicitLexer()).generateLexer().render();
        assertTrue(lexer.contains("class RecordsLexer extends Lexer"));
        assertTrue(lexer.contains("ID"));
        assertTrue(lexer.contains("INT"));

        String listener = generator.generateListener().render();
        assertTrue(listener.contains("interface RecordsListener extends ParseTreeListener"));
        assertTrue(listener.contains("enterPair(@NotNull RecordsParser.PairContext ctx)"));
        assertTrue(listener.contains("exitDocument(@NotNull RecordsParser.DocumentContext ctx)"));
        assertTrue(generator.generateBaseListener().render().contains("class RecordsBaseListener"));

        String visitor = generator.generateVisitor().render();
        assertTrue(visitor.contains("interface RecordsVisitor<Result> extends ParseTreeVisitor<Result>"));
        assertTrue(visitor.contains("visitPair(@NotNull RecordsParser.PairContext ctx)"));
        String baseVisitor = generator.generateBaseVisitor().render();
        assertTrue(baseVisitor.contains("class RecordsBaseVisitor<Result>"));
        assertTrue(baseVisitor.contains("return visitChildren(ctx);"));
        assertEquals(0, grammar.tool.getNumErrors());
    }
}
