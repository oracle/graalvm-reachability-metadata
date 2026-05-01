/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.observer.ObservableProperty;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.VoidType;
import org.junit.jupiter.api.Test;

public class ObservablePropertyTest {
    @Test
    void readsSingleReferencePropertyThroughGeneratedGetAccessor() {
        MethodDeclaration method = new MethodDeclaration(new NodeList<>(), new VoidType(), "calculate");
        SimpleName methodName = method.getName();

        assertThat(ObservableProperty.NAME.camelCaseName()).isEqualTo("name");
        assertThat(ObservableProperty.NAME.getRawValue(method)).isSameAs(methodName);
        assertThat(ObservableProperty.NAME.getValueAsSingleReference(method)).isSameAs(methodName);
    }

    @Test
    void readsMultipleReferencePropertyAsNodeListAndCollection() {
        MethodDeclaration method = new MethodDeclaration(new NodeList<>(), new VoidType(), "add");
        Parameter left = new Parameter(PrimitiveType.intType(), "left");
        Parameter right = new Parameter(PrimitiveType.intType(), "right");
        method.addParameter(left);
        method.addParameter(right);

        NodeList<?> parameters = ObservableProperty.PARAMETERS.getValueAsMultipleReference(method);
        Object[] parameterCollection = ObservableProperty.PARAMETERS.getValueAsCollection(method).toArray();

        assertThat(parameters).hasSize(2);
        assertThat(parameters.get(0)).isSameAs(left);
        assertThat(parameters.get(1)).isSameAs(right);
        assertThat(parameterCollection).containsExactly(left, right);
        assertThat(ObservableProperty.PARAMETERS.isNullOrEmpty(method)).isFalse();
    }

    @Test
    void unwrapsOptionalSingleReferenceProperty() {
        MethodDeclaration method = new MethodDeclaration(new NodeList<>(), new VoidType(), "withBody");
        BlockStmt body = new BlockStmt();
        method.setBody(body);

        assertThat(ObservableProperty.BODY.getRawValue(method)).isEqualTo(method.getBody());
        assertThat(ObservableProperty.BODY.getValueAsSingleReference(method)).isSameAs(body);

        method.setBody(null);

        assertThat(ObservableProperty.BODY.getValueAsSingleReference(method)).isNull();
        assertThat(ObservableProperty.BODY.isNullOrNotPresent(method)).isTrue();
    }

    @Test
    void readsValuePropertiesThroughGeneratedGetAndIsAccessors() {
        SimpleName simpleName = new SimpleName("sampleIdentifier");
        ImportDeclaration staticImport = new ImportDeclaration(new Name("java.util.Collections"), true, true);

        assertThat(ObservableProperty.IDENTIFIER.getValueAsStringAttribute(simpleName)).isEqualTo("sampleIdentifier");
        assertThat(ObservableProperty.IDENTIFIER.isNullOrEmpty(simpleName)).isFalse();
        assertThat(ObservableProperty.STATIC.getRawValue(staticImport)).isEqualTo(Boolean.TRUE);
        assertThat(ObservableProperty.STATIC.getValueAsBooleanAttribute(staticImport)).isTrue();
        assertThat(ObservableProperty.ASTERISK.getValueAsBooleanAttribute(staticImport)).isTrue();
    }

    @Test
    void readsDerivedPropertiesThroughGeneratedHasAccessors() {
        IfStmt ifStmt = new IfStmt(new BooleanLiteralExpr(true), new BlockStmt(), new BlockStmt());

        assertThat(ObservableProperty.THEN_BLOCK.getRawValue(ifStmt)).isEqualTo(Boolean.TRUE);
        assertThat(ObservableProperty.THEN_BLOCK.getValueAsBooleanAttribute(ifStmt)).isTrue();
        assertThat(ObservableProperty.ELSE_BLOCK.getValueAsBooleanAttribute(ifStmt)).isTrue();
        assertThat(ObservableProperty.ELSE_BRANCH.getValueAsBooleanAttribute(ifStmt)).isTrue();
    }

    @Test
    void reportsMissingAccessorForMismatchedPropertyAndNode() {
        MethodDeclaration method = new MethodDeclaration(new NodeList<>(), new VoidType(), "notAModule");

        assertThatThrownBy(() -> ObservableProperty.MODULE.getRawValue(method))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unable to get value for MODULE")
                .hasMessageContaining("notAModule");
    }
}
