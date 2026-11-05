/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JavaParser's AST construction and pretty-printing subsystem to generate a small API class.
public class AstCodeGenerationTest {
    @Test
    public void generatesAndValidatesAConfiguredApiClass() {
        CompilationUnit compilationUnit = new CompilationUnit();
        compilationUnit.setPackageDeclaration("com.example.generated");
        compilationUnit.addImport("java.util.Objects");

        ClassOrInterfaceDeclaration customer = compilationUnit.addClass(
                "Customer", Modifier.Keyword.PUBLIC, Modifier.Keyword.FINAL);
        customer.addField(String.class, "id", Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);

        ConstructorDeclaration constructor = customer.addConstructor(Modifier.Keyword.PUBLIC);
        constructor.addParameter(String.class, "id");
        constructor.getBody().addStatement("this.id = Objects.requireNonNull(id);");

        MethodDeclaration idMethod = customer.addMethod("id", Modifier.Keyword.PUBLIC);
        idMethod.setType(String.class);
        idMethod.getBody().orElseThrow().addStatement("return id;");

        String generatedSource = compilationUnit.toString();
        CompilationUnit parsedSource = StaticJavaParser.parse(generatedSource);
        ClassOrInterfaceDeclaration parsedCustomer = parsedSource.getClassByName("Customer").orElseThrow();

        assertEquals("com.example.generated", parsedSource.getPackageDeclaration().orElseThrow().getNameAsString());
        assertTrue(generatedSource.contains("public final class Customer"));
        assertTrue(generatedSource.contains("this.id = Objects.requireNonNull(id);"));
        assertEquals("String", parsedCustomer.getFieldByName("id").orElseThrow().getElementType().asString());
        assertEquals(1, parsedCustomer.getMethodsByName("id").size());
        assertEquals("String", parsedCustomer.getMethodsByName("id").get(0).getType().asString());
        assertEquals("id", parsedCustomer.getConstructors().get(0).getParameter(0).getNameAsString());
    }
}
