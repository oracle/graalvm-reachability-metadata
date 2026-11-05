/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.javadoc.Javadoc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JavaParser's Javadoc subsystem to extract structured documentation for an API catalog.
public class JavadocDocumentationTest {
    @Test
    public void extractsMethodDocumentationAndTagsForAnApiCatalog() {
        String source = """
                /**
                 * A catalog of products exposed by the service.
                 */
                public class ProductCatalog {
                    /**
                     * Finds a product by its public identifier.
                     *
                     * @param id the identifier supplied by a customer
                     * @return the matching product, when one exists
                     */
                    public String find(String id) {
                        return id;
                    }
                }
                """;

        CompilationUnit compilationUnit = StaticJavaParser.parse(source);
        ClassOrInterfaceDeclaration catalog = compilationUnit.getClassByName("ProductCatalog").orElseThrow();
        MethodDeclaration find = catalog.getMethodsByName("find").stream().findFirst().orElseThrow();
        Javadoc javadoc = find.getJavadoc().orElseThrow();

        assertEquals("Finds a product by its public identifier.", javadoc.getDescription().toText());
        assertEquals(2, javadoc.getBlockTags().size());
        assertEquals("param", javadoc.getBlockTags().get(0).getTagName());
        assertEquals("id", javadoc.getBlockTags().get(0).getName().orElseThrow());
        assertEquals(
                "the identifier supplied by a customer",
                javadoc.getBlockTags().get(0).getContent().toText());
        assertEquals("return", javadoc.getBlockTags().get(1).getTagName());
        assertTrue(javadoc.toText().contains("Finds a product by its public identifier."));
        assertTrue(javadoc.toText().contains("@return the matching product, when one exists"));
    }
}
