/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.antlr4_runtime;

import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenFactory;
import org.antlr.v4.runtime.ListTokenSource;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.misc.Interval;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Exercises ANTLR's token-source adapter subsystem to replay a cached deployment command stream.
public class CachedCommandStreamTest {

    private static final int DEPLOY = 1;
    private static final int SERVICE = 2;
    private static final int VERSION = 3;
    private static final int COMMENT = 4;

    @Test
    @Timeout(60)
    public void replaysCachedTokensWithSourceAndChannelMetadata() {
        TokenFactory<CommonToken> factory = CommonTokenFactory.DEFAULT;
        CommonToken deploy = token(factory, DEPLOY, "deploy", 1, 0, Token.DEFAULT_CHANNEL);
        CommonToken service = token(factory, SERVICE, "checkout", 1, 7, Token.DEFAULT_CHANNEL);
        CommonToken version = token(factory, VERSION, "4.13.2", 1, 16, Token.DEFAULT_CHANNEL);
        CommonToken comment = token(factory, COMMENT, "# production", 1, 23, Token.HIDDEN_CHANNEL);
        ListTokenSource source = new ListTokenSource(List.of(deploy, service, version, comment),
                "deployment-token-cache");
        BufferedTokenStream tokens = new BufferedTokenStream(source);

        tokens.fill();

        assertSame(source, tokens.getTokenSource());
        assertEquals("deployment-token-cache", tokens.getSourceName());
        assertEquals(List.of("deploy", "checkout", "4.13.2", "# production", "EOF"),
                tokens.getTokens().stream().map(Token::getText).toList());
        assertEquals(List.of("# production"),
                tokens.getHiddenTokensToRight(version.getTokenIndex()).stream().map(Token::getText).toList());
        assertEquals("deploycheckout4.13.2", tokens.getText(Interval.of(0, 2)));
        assertEquals(0, deploy.getTokenIndex());
        assertEquals(3, comment.getTokenIndex());
        assertEquals(Token.EOF, tokens.get(4).getType());
    }

    private static CommonToken token(TokenFactory<CommonToken> factory, int type, String text, int line,
            int column, int channel) {
        CommonToken token = factory.create(type, text);
        token.setLine(line);
        token.setCharPositionInLine(column);
        token.setChannel(channel);
        return token;
    }
}
