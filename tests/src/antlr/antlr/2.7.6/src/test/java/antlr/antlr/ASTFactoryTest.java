/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.antlr;

import antlr.ASTFactory;
import antlr.CommonAST;
import antlr.CommonToken;
import antlr.Token;
import antlr.collections.AST;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ASTFactoryTest {
    @Test
    void createsDefaultCommonAstNode() {
        ASTFactory factory = new ASTFactory();

        AST ast = factory.create(17, "root");

        assertThat(ast).isInstanceOf(CommonAST.class);
        assertThat(ast.getType()).isEqualTo(17);
        assertThat(ast.getText()).isEqualTo("root");
    }

    @Test
    void createsAstNodeWithTokenConstructorWhenClassNameIsProvided() {
        ASTFactory factory = new ASTFactory();
        Token token = new CommonToken(23, "from-token");

        AST ast = factory.create(token, "antlr.CommonAST");

        assertThat(ast).isInstanceOf(CommonAST.class);
        assertThat(ast.getType()).isEqualTo(token.getType());
        assertThat(ast.getText()).isEqualTo(token.getText());
    }
}
