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

class ASTFactoryTest {
    @Test
    void createUsesDefaultAstType() {
        ASTFactory factory = new ASTFactory();

        AST ast = factory.create();

        assertThat(ast).isInstanceOf(CommonAST.class);
        assertThat(ast.getType()).isEqualTo(Token.INVALID_TYPE);
        assertThat(ast.getText()).isEqualTo("");
    }

    @Test
    void createWithTokenClassNameUsesTokenConstructor() {
        ASTFactory factory = new ASTFactory();
        CommonToken token = new CommonToken(7, "identifier");

        AST ast = factory.create(token, "antlr.CommonAST");

        assertThat(ast).isInstanceOf(CommonAST.class);
        assertThat(ast.getType()).isEqualTo(7);
        assertThat(ast.getText()).isEqualTo("identifier");
    }
}
