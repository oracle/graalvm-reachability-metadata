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
import com.github.javaparser.ast.visitor.CloneVisitor;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JavaParser's AST cloning and modifier-visitor subsystem to derive a public API view.
public class AstCloningTransformationTest {
    @Test
    public void derivesPublicApiViewWithoutChangingParsedApplicationSource() {
        String source = """
                package billing;

                public class PaymentService {
                    public String charge(String cardNumber) {
                        return cardNumber;
                    }

                    private String auditToken() {
                        return "internal";
                    }
                }
                """;

        CompilationUnit original = StaticJavaParser.parse(source);
        CompilationUnit apiView = (CompilationUnit) original.accept(new CloneVisitor(), null);
        apiView.accept(new PublicSurfaceVisitor(), null);

        assertEquals(2, original.findAll(MethodDeclaration.class).size());
        assertTrue(original.toString().contains("auditToken"));
        assertEquals(1, apiView.findAll(MethodDeclaration.class).size());
        assertEquals("charge", apiView.findFirst(MethodDeclaration.class).orElseThrow().getNameAsString());
        assertTrue(apiView.toString().contains("public String charge(String cardNumber)"));
        assertTrue(!apiView.toString().contains("auditToken"));
    }

    private static final class PublicSurfaceVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodDeclaration declaration, Void argument) {
            MethodDeclaration transformed = (MethodDeclaration) super.visit(declaration, argument);
            return transformed.isPrivate() ? null : transformed;
        }
    }
}
