/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JavaParser's symbol-resolution subsystem to identify the API behind a service call.
public class SymbolResolutionTest {
    @Test
    public void resolvesServiceCallsForApiDocumentation() {
        String source = """
                package app;

                class Invoice {
                }

                class InvoiceService {
                    public Invoice find(String id) {
                        return new Invoice();
                    }
                }

                class Checkout {
                    void run(InvoiceService service) {
                        service.find("A-17");
                    }
                }
                """;

        ParserConfiguration configuration = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));
        CompilationUnit compilationUnit = new JavaParser(configuration)
                .parse(source)
                .getResult()
                .orElseThrow();
        MethodCallExpr call = compilationUnit.findFirst(
                MethodCallExpr.class, methodCall -> methodCall.getNameAsString().equals("find"))
                .orElseThrow();

        ResolvedMethodDeclaration method = call.resolve();

        assertEquals("find", method.getName());
        assertEquals("app.InvoiceService", method.declaringType().getQualifiedName());
        assertEquals("app.Invoice", method.getReturnType().describe());
        assertEquals("id", method.getParam(0).getName());
    }
}
