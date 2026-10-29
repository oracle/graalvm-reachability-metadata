/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tunnelvisionlabs.antlr4;

import org.antlr.v4.Tool;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.ast.GrammarRootAST;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class GrammarTestSupport {
    private GrammarTestSupport() {
    }

    static Grammar process(String source) {
        Tool tool = new Tool();
        GrammarRootAST ast = tool.parseGrammarFromString(source);
        assertNotNull(ast);
        assertEquals(0, tool.getNumErrors(), "Grammar parsing must succeed");
        Grammar grammar = tool.createGrammar(ast);
        grammar.fileName = grammar.name + ".g4";
        tool.process(grammar, false);
        assertEquals(0, tool.getNumErrors(), "Grammar processing must succeed");
        return grammar;
    }
}
