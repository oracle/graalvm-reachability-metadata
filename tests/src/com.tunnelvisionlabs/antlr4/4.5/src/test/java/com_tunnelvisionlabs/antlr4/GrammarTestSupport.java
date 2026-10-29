/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tunnelvisionlabs.antlr4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.antlr.v4.Tool;
import org.antlr.v4.codegen.CodeGenerator;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.ast.GrammarRootAST;
import org.stringtemplate.v4.ST;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class GrammarTestSupport {
    private GrammarTestSupport() {
    }

    static String render(CodeGenerator generator, ST template, Path outputDirectory) throws IOException {
        generator.tool.outputDirectory = outputDirectory.toString();
        generator.g.fileName = outputDirectory.resolve(generator.g.name + ".g4").toString();
        generator.write(template, "generated.java");
        assertEquals(0, generator.tool.getNumErrors(), "Code generation must succeed");
        return Files.readString(outputDirectory.resolve("generated.java"));
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
