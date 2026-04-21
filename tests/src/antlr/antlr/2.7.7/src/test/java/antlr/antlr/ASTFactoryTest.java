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
    void createsDefaultAndCustomAstNodes() {
        ASTFactory factory = new ASTFactory();

        AST defaultNode = factory.create();
        assertThat(defaultNode).isInstanceOf(CommonAST.class);
        assertThat(defaultNode.getType()).isEqualTo(Token.INVALID_TYPE);

        AST namedNode = factory.create(Token.MIN_USER_TYPE, "named", NamedAst.class.getName());
        assertThat(namedNode).isInstanceOf(NamedAst.class);
        assertThat(namedNode.getType()).isEqualTo(Token.MIN_USER_TYPE);
        assertThat(namedNode.getText()).isEqualTo("named");

        CommonToken token = new CommonToken(Token.MIN_USER_TYPE + 1, "from-token");
        AST tokenCtorNode = factory.create(token, TokenCtorAwareAst.class.getName());
        assertThat(tokenCtorNode).isInstanceOf(TokenCtorAwareAst.class);
        assertThat(((TokenCtorAwareAst) tokenCtorNode).constructedFromToken).isTrue();
        assertThat(tokenCtorNode.getType()).isEqualTo(Token.MIN_USER_TYPE + 1);
        assertThat(tokenCtorNode.getText()).isEqualTo("from-token");
    }

    public static final class NamedAst extends CommonAST {
        public NamedAst() {
        }
    }

    public static final class TokenCtorAwareAst extends CommonAST {
        private boolean constructedFromToken;

        public TokenCtorAwareAst() {
        }

        public TokenCtorAwareAst(Token token) {
            super(token);
            this.constructedFromToken = true;
        }
    }
}
