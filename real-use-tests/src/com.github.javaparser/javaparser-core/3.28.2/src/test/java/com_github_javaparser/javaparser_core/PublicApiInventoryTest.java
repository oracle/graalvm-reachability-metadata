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
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JavaParser's AST visitor subsystem to build a public API inventory from source code.
public class PublicApiInventoryTest {
    @Test
    public void inventoriesPublicMethodsForAnApiReport() {
        String source = """
                package app;

                public class Catalog {
                    public String find(String id) {
                        return id;
                    }

                    private void refreshCache() {
                    }

                    public List<String> ids() {
                        return List.of("one", "two");
                    }
                }
                """;

        CompilationUnit compilationUnit = StaticJavaParser.parse(source);
        List<ApiMethod> publicMethods = new ArrayList<>();
        new PublicMethodVisitor().visit(compilationUnit, publicMethods);

        assertEquals(
                List.of(
                        new ApiMethod("find", "String"),
                        new ApiMethod("ids", "List<String>")),
                publicMethods);
    }

    private static final class PublicMethodVisitor extends VoidVisitorAdapter<List<ApiMethod>> {
        @Override
        public void visit(MethodDeclaration method, List<ApiMethod> publicMethods) {
            if (method.isPublic()) {
                publicMethods.add(new ApiMethod(method.getNameAsString(), method.getType().asString()));
            }
            super.visit(method, publicMethods);
        }
    }

    private record ApiMethod(String name, String returnType) {
    }
}
