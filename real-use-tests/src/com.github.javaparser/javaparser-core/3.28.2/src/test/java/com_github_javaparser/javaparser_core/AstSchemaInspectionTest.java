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
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.metamodel.BaseNodeMetaModel;
import com.github.javaparser.metamodel.JavaParserMetaModel;
import com.github.javaparser.metamodel.PropertyMetaModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JavaParser's meta-model introspection subsystem to build a schema report for a source API.
public class AstSchemaInspectionTest {
    @Test
    public void describesMethodShapeForAnApiSchemaReport() {
        String source = """
                package billing;

                public class InvoiceService {
                    public String find(String id) {
                        return id;
                    }
                }
                """;

        CompilationUnit compilationUnit = StaticJavaParser.parse(source);
        MethodDeclaration method = compilationUnit.findFirst(MethodDeclaration.class).orElseThrow();
        BaseNodeMetaModel methodModel = JavaParserMetaModel.getNodeMetaModel(MethodDeclaration.class).orElseThrow();
        List<PropertySchema> properties = methodModel.getAllPropertyMetaModels().stream()
                .map(property -> propertySchema(property, method))
                .collect(Collectors.toList());
        ApiSchema schema = new ApiSchema(methodModel.getTypeName(), properties);

        assertEquals("MethodDeclaration", schema.nodeType());
        assertTrue(methodModel.isInstanceOfMetaModel(JavaParserMetaModel.callableDeclarationMetaModel));
        assertTrue(schema.propertyNames().containsAll(List.of("name", "type", "parameters", "body")));
        assertTrue(schema.property("name").value().contains("find"));
        assertTrue(schema.property("type").value().contains("String"));
        assertTrue(schema.property("parameters").value().contains("id"));
        assertTrue(schema.property("body").value().contains("return id;"));
    }

    private PropertySchema propertySchema(PropertyMetaModel property, MethodDeclaration method) {
        Object value = property.getValue(method);
        String renderedValue = value instanceof Node node ? node.toString() : String.valueOf(value);
        return new PropertySchema(property.getName(), property.getTypeNameGenerified(), renderedValue);
    }

    private record ApiSchema(String nodeType, List<PropertySchema> properties) {
        private List<String> propertyNames() {
            return properties.stream().map(PropertySchema::name).toList();
        }

        private PropertySchema property(String name) {
            return properties.stream()
                    .filter(property -> property.name().equals(name))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private record PropertySchema(String name, String type, String value) {
    }
}
