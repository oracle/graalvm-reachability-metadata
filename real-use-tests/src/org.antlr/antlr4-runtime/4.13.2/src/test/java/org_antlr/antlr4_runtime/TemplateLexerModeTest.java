/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.antlr4_runtime;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenFactory;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.atn.ATN;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ANTLR's lexer-mode subsystem to render a user-facing message template.
public class TemplateLexerModeTest {

    @Test
    @Timeout(60)
    public void rendersExpressionsWhileReturningToTemplateMode() {
        String source = "Hello ${user + team}!";
        TemplateLexer lexer = new TemplateLexer(CharStreams.fromString(source, "welcome.tpl"));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();

        assertEquals(List.of(
                        "Hello ", "${", "user", " ", "+", " ", "team", "}", "!"),
                tokens.getTokens().stream().filter(token -> token.getType() != Token.EOF)
                        .map(Token::getText).toList());
        assertEquals("Hello AdaPlatform!", render(tokens, Map.of("user", "Ada", "team", "Platform")));
        assertEquals(TemplateLexer.DEFAULT_MODE, lexer.currentMode());
        assertEquals("welcome.tpl", lexer.getSourceName());
    }

    private static String render(CommonTokenStream tokens, Map<String, String> values) {
        StringBuilder rendered = new StringBuilder();
        for (Token token : tokens.getTokens()) {
            switch (token.getType()) {
                case TemplateLexer.TEXT -> rendered.append(token.getText());
                case TemplateLexer.IDENTIFIER -> rendered.append(values.get(token.getText()));
                case TemplateLexer.OPEN_EXPRESSION, TemplateLexer.CLOSE_EXPRESSION, TemplateLexer.PLUS,
                        TemplateLexer.WHITESPACE, Token.EOF -> {
                }
                default -> throw new AssertionError("Unexpected token type: " + token.getType());
            }
        }
        return rendered.toString();
    }

    private static final class TemplateLexer extends Lexer {

        private static final int TEXT = 1;
        private static final int OPEN_EXPRESSION = 2;
        private static final int IDENTIFIER = 3;
        private static final int CLOSE_EXPRESSION = 4;
        private static final int PLUS = 5;
        private static final int WHITESPACE = 6;
        private static final int EXPRESSION_MODE = 1;
        private static final String[] LITERAL_NAMES = {null, null, "'${'", null, "'}'", "'+'", null};
        private static final String[] SYMBOLIC_NAMES = {
                null, "TEXT", "OPEN_EXPRESSION", "IDENTIFIER", "CLOSE_EXPRESSION", "PLUS", "WHITESPACE"
        };
        private static final Vocabulary VOCABULARY = new VocabularyImpl(LITERAL_NAMES, SYMBOLIC_NAMES);
        private static final String[] RULE_NAMES = {
                "TEXT", "OPEN_EXPRESSION", "IDENTIFIER", "CLOSE_EXPRESSION", "PLUS", "WHITESPACE"
        };
        private static final String[] MODE_NAMES = {"DEFAULT_MODE", "EXPRESSION_MODE"};

        private int currentLine = 1;
        private int currentCharPositionInLine;

        private TemplateLexer(CharStream input) {
            super(input);
            setTokenFactory(new CommonTokenFactory(true));
        }

        @Override
        public Token nextToken() {
            if (_input.LA(1) == IntStream.EOF) {
                return emitEOF();
            }
            int startIndex = getCharIndex();
            int startLine = getLine();
            int startPosition = getCharPositionInLine();
            if (_mode == DEFAULT_MODE) {
                if (_input.LA(1) == '$' && _input.LA(2) == '{') {
                    consumeCurrentCharacter();
                    consumeCurrentCharacter();
                    pushMode(EXPRESSION_MODE);
                    return emitToken(OPEN_EXPRESSION, Token.DEFAULT_CHANNEL, "${", startIndex, startLine,
                            startPosition);
                }
                return emitToken(TEXT, Token.DEFAULT_CHANNEL, consumeTemplateText(), startIndex, startLine,
                        startPosition);
            }
            int current = _input.LA(1);
            if (current == '}') {
                consumeCurrentCharacter();
                popMode();
                return emitToken(CLOSE_EXPRESSION, Token.DEFAULT_CHANNEL, "}", startIndex, startLine,
                        startPosition);
            }
            if (Character.isWhitespace(current)) {
                return emitToken(WHITESPACE, Token.HIDDEN_CHANNEL, consumeWhile(Character::isWhitespace),
                        startIndex, startLine, startPosition);
            }
            if (current == '+') {
                consumeCurrentCharacter();
                return emitToken(PLUS, Token.DEFAULT_CHANNEL, "+", startIndex, startLine, startPosition);
            }
            if (Character.isLetter(current)) {
                return emitToken(IDENTIFIER, Token.DEFAULT_CHANNEL,
                        consumeWhile(Character::isLetterOrDigit), startIndex, startLine, startPosition);
            }
            throw new IllegalArgumentException("Unsupported template character: " + (char) current);
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
            return "TemplateLexer";
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
            return MODE_NAMES;
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

        private int currentMode() {
            return _mode;
        }

        private String consumeTemplateText() {
            return consumeWhile(value -> value != IntStream.EOF
                    && !(value == '$' && _input.LA(2) == '{'));
        }

        private String consumeWhile(java.util.function.IntPredicate predicate) {
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
