/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.antlr4_runtime;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.atn.ATN;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ANTLR's parser error-listener subsystem to report invalid deployment settings.
public class ErrorListenerDiagnosticsTest {

    @Test
    @Timeout(60)
    public void reportsDuplicateSettingWithLocation() {
        String source = "profile \"production\" {\n"
                + "  timeout = 30,\n"
                + "  timeout = 45,\n"
                + "  region = \"eu-west\"\n"
                + "}";
        CharStream input = CharStreams.fromString(source, "deployment.conf");
        CommonTokenStream tokens = new CommonTokenStream(new ConfigurationLexer(input));
        ConfigurationParser parser = new ConfigurationParser(tokens);
        DiagnosticListener diagnostics = new DiagnosticListener();
        parser.removeErrorListeners();
        parser.addErrorListener(diagnostics);

        parser.document();

        assertEquals(List.of(new Diagnostic(3, 2, "duplicate setting 'timeout'")), diagnostics.items);
        assertEquals(1, parser.getNumberOfSyntaxErrors());
        assertEquals("deployment.conf", input.getSourceName());
    }

    private record Diagnostic(int line, int column, String message) {
    }

    private static final class DiagnosticListener extends BaseErrorListener {

        private final List<Diagnostic> items = new ArrayList<>();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                int charPositionInLine, String message, RecognitionException exception) {
            items.add(new Diagnostic(line, charPositionInLine, message));
        }
    }

    private static final class ConfigurationLexer extends Lexer {

        private static final int PROFILE = 1;
        private static final int STRING = 2;
        private static final int IDENTIFIER = 3;
        private static final int NUMBER = 4;
        private static final int LEFT_BRACE = 5;
        private static final int RIGHT_BRACE = 6;
        private static final int EQUALS = 7;
        private static final int COMMA = 8;
        private static final int WS = 9;
        private static final String[] LITERAL_NAMES = {
                null, null, null, null, null, "'{'", "'}'", "'='", "','"
        };
        private static final String[] SYMBOLIC_NAMES = {
                null, "PROFILE", "STRING", "IDENTIFIER", "NUMBER", "LEFT_BRACE", "RIGHT_BRACE", "EQUALS",
                "COMMA", "WS"
        };
        private static final Vocabulary VOCABULARY = new VocabularyImpl(LITERAL_NAMES, SYMBOLIC_NAMES);
        private static final String[] RULE_NAMES = {
                "PROFILE", "STRING", "IDENTIFIER", "NUMBER", "LEFT_BRACE", "RIGHT_BRACE", "EQUALS", "COMMA",
                "WS"
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
                return emitToken(STRING, Token.DEFAULT_CHANNEL, consumeString(), startIndex, startLine, startPosition);
            }
            if (current == '-' || Character.isDigit(current)) {
                return emitToken(NUMBER, Token.DEFAULT_CHANNEL,
                        consumeWhile(value -> value == '-' || Character.isDigit(value)), startIndex, startLine,
                        startPosition);
            }
            if (current == '{' || current == '}' || current == '=' || current == ',') {
                consumeCurrentCharacter();
                int type = switch (current) {
                    case '{' -> LEFT_BRACE;
                    case '}' -> RIGHT_BRACE;
                    case '=' -> EQUALS;
                    default -> COMMA;
                };
                return emitToken(type, Token.DEFAULT_CHANNEL, String.valueOf((char) current), startIndex, startLine,
                        startPosition);
            }
            if (Character.isLetter(current) || current == '_') {
                String text = consumeWhile(value -> Character.isLetterOrDigit(value) || value == '_' || value == '-');
                int type = text.equals("profile") ? PROFILE : IDENTIFIER;
                return emitToken(type, Token.DEFAULT_CHANNEL, text, startIndex, startLine, startPosition);
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

    private static final class ConfigurationParser extends Parser {

        private static final int PROFILE = ConfigurationLexer.PROFILE;
        private static final int STRING = ConfigurationLexer.STRING;
        private static final int IDENTIFIER = ConfigurationLexer.IDENTIFIER;
        private static final int NUMBER = ConfigurationLexer.NUMBER;
        private static final int LEFT_BRACE = ConfigurationLexer.LEFT_BRACE;
        private static final int RIGHT_BRACE = ConfigurationLexer.RIGHT_BRACE;
        private static final int EQUALS = ConfigurationLexer.EQUALS;
        private static final int COMMA = ConfigurationLexer.COMMA;
        private static final String[] RULE_NAMES = {"document", "setting"};

        private ConfigurationParser(TokenStream input) {
            super(input);
        }

        private void document() {
            ParserRuleContext context = new ParserRuleContext(_ctx, getState());
            enterRule(context, 0, 0);
            try {
                enterOuterAlt(context, 1);
                match(PROFILE);
                match(STRING);
                match(LEFT_BRACE);
                Set<String> settingNames = new LinkedHashSet<>();
                while (getInputStream().LA(1) == IDENTIFIER) {
                    Token name = match(IDENTIFIER);
                    match(EQUALS);
                    consume();
                    if (!settingNames.add(name.getText())) {
                        notifyErrorListeners(name, "duplicate setting '" + name.getText() + "'", null);
                    }
                    if (getInputStream().LA(1) == COMMA) {
                        consume();
                    }
                }
                match(RIGHT_BRACE);
                match(Token.EOF);
            } finally {
                exitRule();
            }
        }

        @Override
        public String getGrammarFileName() {
            return "ConfigurationParser";
        }

        @Override
        public String[] getTokenNames() {
            return ConfigurationLexer.SYMBOLIC_NAMES;
        }

        @Override
        public Vocabulary getVocabulary() {
            return ConfigurationLexer.VOCABULARY;
        }

        @Override
        public String[] getRuleNames() {
            return RULE_NAMES;
        }

        @Override
        public int getTokenType(String tokenName) {
            return switch (tokenName) {
                case "PROFILE" -> PROFILE;
                case "STRING" -> STRING;
                case "IDENTIFIER" -> IDENTIFIER;
                case "NUMBER" -> NUMBER;
                case "LEFT_BRACE" -> LEFT_BRACE;
                case "RIGHT_BRACE" -> RIGHT_BRACE;
                case "EQUALS" -> EQUALS;
                case "COMMA" -> COMMA;
                default -> Token.INVALID_TYPE;
            };
        }

        @Override
        public ATN getATN() {
            return null;
        }
    }
}
