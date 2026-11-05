/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JavaParser's lexical-preservation subsystem to update source without discarding its formatting and comments.
public class SourceMigrationTest {
    @Test
    public void updatesAnInventoryMethodWhilePreservingTheSourceLayout() {
        String source = """
                package app;

                import java.util.List;

                // The inventory export is consumed by the warehouse service.
                public class Inventory {
                    public List<String> names() {
                        return List.of("first");
                    }
                }
                """;

        CompilationUnit compilationUnit = LexicalPreservingPrinter.setup(StaticJavaParser.parse(source));
        MethodDeclaration namesMethod = compilationUnit.findFirst(
                MethodDeclaration.class, method -> method.getNameAsString().equals("names"))
                .orElseThrow();
        MethodCallExpr listFactory = namesMethod.findFirst(
                MethodCallExpr.class, call -> call.getNameAsString().equals("of"))
                .orElseThrow();

        compilationUnit.addImport("java.util.Collections");
        listFactory.addArgument(new StringLiteralExpr("second"));

        String migratedSource = LexicalPreservingPrinter.print(compilationUnit);

        assertEquals(2, listFactory.getArguments().size());
        assertTrue(migratedSource.contains("import java.util.Collections;"));
        assertTrue(migratedSource.contains("// The inventory export is consumed by the warehouse service."));
        assertTrue(migratedSource.contains("return List.of(\"first\", \"second\");"));
    }
}
