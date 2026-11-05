/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.observer.AstObserverAdapter;
import com.github.javaparser.ast.observer.AstObserver.ListChangeType;
import com.github.javaparser.ast.observer.ObservableProperty;
import com.github.javaparser.ast.stmt.Statement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JavaParser's AST observer subsystem to audit application source refactorings.
public class AstChangeTrackingTest {
    @Test
    public void recordsRefactoringChangesWhileUpdatingAServiceMethod() {
        String source = """
                package app;

                public class CustomerService {
                    public String find(String customerId) {
                    }
                }
                """;

        CompilationUnit compilationUnit = StaticJavaParser.parse(source);
        List<Change> changes = new ArrayList<>();
        compilationUnit.registerForSubtree(new RefactoringAudit(changes));

        MethodDeclaration method = compilationUnit.findFirst(MethodDeclaration.class).orElseThrow();
        method.setName("findById");
        method.getParameter(0).setName("id");
        Statement lookup = StaticJavaParser.parseStatement("return repository.lookup(id);");
        method.getBody().orElseThrow().addStatement(lookup);

        assertEquals("findById", method.getNameAsString());
        assertEquals("id", method.getParameter(0).getNameAsString());
        assertEquals("return repository.lookup(id);", method.getBody().orElseThrow()
                .getStatement(0).toString().trim());
        assertTrue(changes.contains(new Change("property", "NAME:find->findById")));
        assertTrue(changes.contains(new Change("property", "NAME:customerId->id")));
        assertTrue(changes.stream().anyMatch(change -> change.kind().equals("addition")
                && change.detail().equals("return repository.lookup(id);")));
    }

    private static final class RefactoringAudit extends AstObserverAdapter {
        private final List<Change> changes;

        private RefactoringAudit(List<Change> changes) {
            this.changes = changes;
        }

        @Override
        public void propertyChange(
                Node node, ObservableProperty property, Object oldValue, Object newValue) {
            if ((node instanceof MethodDeclaration || node instanceof Parameter)
                    && property == ObservableProperty.NAME) {
                changes.add(new Change("property", "NAME:" + oldValue + "->" + newValue));
            }
        }

        @Override
        public void listChange(
                NodeList<?> list, ListChangeType type, int index, Node node) {
            if (type == ListChangeType.ADDITION && node instanceof Statement) {
                changes.add(new Change("addition", node.toString().trim()));
            }
        }
    }

    private record Change(String kind, String detail) {
    }
}
