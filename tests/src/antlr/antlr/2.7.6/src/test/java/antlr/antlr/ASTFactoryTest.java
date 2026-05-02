/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.antlr;

import antlr.ASTFactory;
import antlr.CommonAST;
import antlr.CommonASTWithHiddenTokens;
import antlr.CommonHiddenStreamToken;
import antlr.collections.AST;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ASTFactoryTest {
    @Test
    void createsDefaultCommonAstNode() {
        ASTFactory factory = new ASTFactory();

        AST node = factory.create(101, "root");

        assertThat(node).isInstanceOf(CommonAST.class);
        assertThat(node.getType()).isEqualTo(101);
        assertThat(node.getText()).isEqualTo("root");
    }

    @Test
    void createsAstNodeUsingPublicTokenConstructor() {
        ASTFactory factory = new ASTFactory();
        CommonHiddenStreamToken token = new CommonHiddenStreamToken(202, "leaf");

        AST node = factory.create(token, CommonASTWithHiddenTokens.class.getName());

        assertThat(node).isInstanceOf(CommonASTWithHiddenTokens.class);
        assertThat(node.getType()).isEqualTo(token.getType());
        assertThat(node.getText()).isEqualTo(token.getText());
    }
}
