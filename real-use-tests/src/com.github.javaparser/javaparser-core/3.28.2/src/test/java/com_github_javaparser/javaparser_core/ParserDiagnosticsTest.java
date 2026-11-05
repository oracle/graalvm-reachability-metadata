/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JavaParser's parser-diagnostics subsystem to report a source error and its location.
public class ParserDiagnosticsTest {
    @Test
    public void reportsInvalidSourceForAnEditor() {
        String source = """
                package app;

                public class Invoice {
                    public void submit() {
                        int attempts = ;
                    }
                }
                """;

        ParseResult<CompilationUnit> result = new JavaParser().parse(source);

        assertFalse(result.isSuccessful());
        assertTrue(result.getResult().isPresent());
        assertEquals(1, result.getProblems().size());

        Problem problem = result.getProblems().get(0);
        assertTrue(problem.getMessage().contains("Parse error"));
        assertTrue(problem.getLocation().isPresent());
        assertEquals(5, problem.getLocation().orElseThrow().getBegin().getRange().orElseThrow().begin.line);
    }
}
