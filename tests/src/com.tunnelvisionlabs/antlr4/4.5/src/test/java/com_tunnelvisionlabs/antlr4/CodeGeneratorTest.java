/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tunnelvisionlabs.antlr4;

import org.antlr.v4.codegen.CodeGenerator;
import org.antlr.v4.codegen.Target;
import org.antlr.v4.tool.Grammar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class CodeGeneratorTest {
    @Test
    void selectsJavaTargetFromGrammarLanguageOption() {
        Grammar grammar = GrammarTestSupport.process("""
                parser grammar Commands;
                options { language=Java; }
                tokens { WORD }
                command: WORD EOF;
                """);
        CodeGenerator generator = new CodeGenerator(grammar);
        Target target = generator.getTarget();

        assertNotNull(target);
        assertEquals("Java", target.getLanguage());
        assertSame(generator, target.getCodeGenerator());
        assertSame(target, generator.getTarget());
        assertEquals("\"line\\n\"", target.getTargetStringLiteralFromString("line\n"));
        assertTrue(generator.getTemplates().isDefined("ParserFile"));
        assertEquals("Commands.java", generator.getRecognizerFileName());
        assertEquals("CommandsListener.java", generator.getListenerFileName());
        assertEquals("CommandsVisitor.java", generator.getVisitorFileName());
        assertEquals("CommandsBaseListener.java", generator.getBaseListenerFileName());
        assertEquals("CommandsBaseVisitor.java", generator.getBaseVisitorFileName());
        assertEquals(0, grammar.tool.getNumErrors());
    }
}
