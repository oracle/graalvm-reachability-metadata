/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.junit.jupiter.api.Test;

public class LexicalPreservingPrinterTest {
    @Test
    void preservesSourceWhenAddingToDirectNodeListProperty() {
        CompilationUnit compilationUnit = LexicalPreservingPrinter.setup(StaticJavaParser.parse("class Sample {}"));

        compilationUnit.addImport("java.util.List");

        String printedSource = LexicalPreservingPrinter.print(compilationUnit);
        assertThat(compilationUnit.getImports())
                .extracting(importDeclaration -> importDeclaration.getNameAsString())
                .contains("java.util.List");
        assertThat(printedSource).contains("import java.util.List;").contains("class Sample {}");
    }

    @Test
    void preservesSourceWhenAddingToOptionalNodeListProperty() {
        CompilationUnit compilationUnit = LexicalPreservingPrinter.setup(StaticJavaParser.parse("""
                class Sample {
                    void test() {
                        java.util.Collections.<String>emptyList();
                    }
                }
                """));
        MethodCallExpr methodCall = compilationUnit
                .findFirst(MethodCallExpr.class, call -> call.getNameAsString().equals("emptyList"))
                .orElseThrow();
        NodeList<Type> typeArguments = methodCall.getTypeArguments().orElseThrow();

        typeArguments.add(StaticJavaParser.parseClassOrInterfaceType("Integer"));

        String printedSource = LexicalPreservingPrinter.print(compilationUnit);
        assertThat(methodCall.getTypeArguments()).hasValueSatisfying(arguments -> assertThat(arguments).hasSize(2));
        assertThat(printedSource).contains("java.util.Collections.<String, Integer>emptyList();");
    }
}
