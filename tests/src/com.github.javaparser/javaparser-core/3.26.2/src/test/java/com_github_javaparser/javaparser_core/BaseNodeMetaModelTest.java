/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.metamodel.BaseNodeMetaModel;
import com.github.javaparser.metamodel.JavaParserMetaModel;
import com.github.javaparser.metamodel.PropertyMetaModel;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class BaseNodeMetaModelTest {
    @Test
    void constructsNodeThroughAllFieldsConstructorMetadata() {
        BaseNodeMetaModel metaModel = JavaParserMetaModel.simpleNameMetaModel;
        PropertyMetaModel identifierParameter = metaModel.getConstructorParameters().get(0);
        Map<String, Object> parameters = Map.of(identifierParameter.getName(), "generatedName");

        Node node = metaModel.construct(parameters);

        assertThat(identifierParameter.getName()).isEqualTo("identifier");
        assertThat(node).isInstanceOf(SimpleName.class);
        assertThat(((SimpleName) node).asString()).isEqualTo("generatedName");
        assertThat(node.getMetaModel()).isSameAs(metaModel);
    }
}
