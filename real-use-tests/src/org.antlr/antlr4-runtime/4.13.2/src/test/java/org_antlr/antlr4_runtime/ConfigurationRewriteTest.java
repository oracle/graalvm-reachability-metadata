/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.antlr4_runtime;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ANTLR's token-stream rewriting subsystem to sanitize a service configuration.
public class ConfigurationRewriteTest {

    @Test
    @Timeout(60)
    public void createsSanitizedAndAuditViewsFromOneTokenStream() {
        String source = "service checkout {\n"
                + "  endpoint = \"https://internal.example\"\n"
                + "  apiKey = \"secret-123\"\n"
                + "}\n";
        CommonTokenStream tokens = new CommonTokenStream(new ConfigurationLexer(
                CharStreams.fromString(source, "deployment.conf")));
        tokens.fill();

        Token serviceName = findToken(tokens, "checkout");
        Token secret = findToken(tokens, "\"secret-123\"");
        Token apiKey = findToken(tokens, "apiKey");
        Token closingBrace = findToken(tokens, "}");

        TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);
        rewriter.insertBefore(serviceName, "production-");
        rewriter.replace(secret, "\"[REDACTED]\"");
        rewriter.insertAfter(closingBrace, "\n# sanitized by deployment tool");

        assertEquals("service production-checkout {\n"
                        + "  endpoint = \"https://internal.example\"\n"
                        + "  apiKey = \"[REDACTED]\"\n"
                        + "}\n# sanitized by deployment tool\n",
                rewriter.getText());

        rewriter.insertAfter("audit", apiKey, " /* sensitive */");
        assertEquals("service checkout {\n"
                        + "  endpoint = \"https://internal.example\"\n"
                        + "  apiKey /* sensitive */ = \"secret-123\"\n"
                        + "}\n",
                rewriter.getText("audit"));
    }

    private static Token findToken(CommonTokenStream tokens, String text) {
        return tokens.getTokens().stream()
                .filter(token -> token.getText().equals(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing token: " + text));
    }

    private static final class ConfigurationLexer extends Lexer {

        private static final int IDENTIFIER = 1;
        private static final int EQUALS = 2;
        private static final int STRING = 3;
        private static final int LEFT_BRACE = 4;
        private static final int RIGHT_BRACE = 5;
        private static final int WS = 6;
        private static final String[] LITERAL_NAMES = {
                null, null, "'='", null, "'{'", "'}'"
        };
        private static final String[] SYMBOLIC_NAMES = {
                null, "IDENTIFIER", "EQUALS", "STRING", "LEFT_BRACE", "RIGHT_BRACE", "WS"
        };
        private static final Vocabulary VOCABULARY = new VocabularyImpl(LITERAL_NAMES, SYMBOLIC_NAMES);
        private static final String[] RULE_NAMES = {
                "IDENTIFIER", "EQUALS", "STRING", "LEFT_BRACE", "RIGHT_BRACE", "WS"
        };

        private int currentLine = 1;
        private int currentCharPositionInLine;

        private ConfigurationLexer(CharStream input) {
            super(input);
        }

        @Override
        public Token nextToken() {
            if (_input.LA(1) == IntStream.EOF) {
                return emitEOF();
            }

            int startIndex = getCharIndex();
            int startLine = getLine();
            int startPosition = getCharPositionInLine();
            int current = _input.LA(1);
            if (Character.isWhitespace(current)) {
                return emitToken(WS, Token.HIDDEN_CHANNEL, consumeWhile(Character::isWhitespace), startIndex,
                        startLine, startPosition);
            }
            if (current == '"') {
                return emitToken(STRING, Token.DEFAULT_CHANNEL, consumeString(), startIndex, startLine,
                        startPosition);
            }
            if (current == '=' || current == '{' || current == '}') {
                consumeCurrentCharacter();
                int type = switch (current) {
                    case '=' -> EQUALS;
                    case '{' -> LEFT_BRACE;
                    default -> RIGHT_BRACE;
                };
                return emitToken(type, Token.DEFAULT_CHANNEL, String.valueOf((char) current), startIndex,
                        startLine, startPosition);
            }
            if (Character.isLetter(current)) {
                return emitToken(IDENTIFIER, Token.DEFAULT_CHANNEL,
                        consumeWhile(value -> Character.isLetterOrDigit(value) || value == '-' || value == '.'),
                        startIndex, startLine, startPosition);
            }
            throw new IllegalArgumentException("Unsupported character: " + (char) current);
        }

        @Override
        public int getLine() {
            return currentLine;
        }

        @Override
        public int getCharPositionInLine() {
            return currentCharPositionInLine;
        }

        @Override
        public void setLine(int line) {
            currentLine = line;
        }

        @Override
        public void setCharPositionInLine(int charPositionInLine) {
            currentCharPositionInLine = charPositionInLine;
        }

        @Override
        public String getGrammarFileName() {
            return "ConfigurationLexer";
        }

        @Override
        public String[] getRuleNames() {
            return RULE_NAMES;
        }

        @Override
        public String getSerializedATN() {
            return "";
        }

        @Override
        public String[] getChannelNames() {
            return new String[]{"DEFAULT_TOKEN_CHANNEL", "HIDDEN"};
        }

        @Override
        public String[] getModeNames() {
            return new String[]{"DEFAULT_MODE"};
        }

        @Override
        public Vocabulary getVocabulary() {
            return VOCABULARY;
        }

        @Override
        public String[] getTokenNames() {
            return SYMBOLIC_NAMES;
        }

        @Override
        public ATN getATN() {
            return null;
        }

        private String consumeString() {
            StringBuilder text = new StringBuilder();
            text.append((char) consumeCurrentCharacter());
            while (_input.LA(1) != IntStream.EOF) {
                int current = consumeCurrentCharacter();
                text.append((char) current);
                if (current == '"') {
                    return text.toString();
                }
            }
            throw new IllegalArgumentException("Unterminated string literal");
        }

        private String consumeWhile(IntPredicate predicate) {
            StringBuilder text = new StringBuilder();
            while (_input.LA(1) != IntStream.EOF && predicate.test(_input.LA(1))) {
                text.append((char) consumeCurrentCharacter());
            }
            return text.toString();
        }

        private int consumeCurrentCharacter() {
            int current = _input.LA(1);
            _input.consume();
            if (current == '\n') {
                currentLine++;
                currentCharPositionInLine = 0;
            } else {
                currentCharPositionInLine++;
            }
            return current;
        }

        private Token emitToken(int type, int channel, String text, int startIndex, int line, int startPosition) {
            TokenFactory<?> tokenFactory = getTokenFactory();
            return tokenFactory.create(_tokenFactorySourcePair, type, text, channel, startIndex,
                    getCharIndex() - 1, line, startPosition);
        }
    }
}
