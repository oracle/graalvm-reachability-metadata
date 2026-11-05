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
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the ANTLR lexer, token-stream, and parse-tree subsystems to load a service configuration.
public class ServiceConfigurationTest {

    @Test
    @Timeout(60)
    public void loadsConfigurationThroughRuntimePipeline() {
        String source = "profile \"production\" { timeout = 30, region = \"eu-west\" # deploy target\n}";
        CharStream input = CharStreams.fromString(source, "service.conf");
        ServiceLexer lexer = new ServiceLexer(input);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        ServiceParser parser = new ServiceParser(tokenStream);

        ServiceParser.DocumentContext document = parser.document();
        ConfigurationListener listener = new ConfigurationListener();
        ParseTreeWalker.DEFAULT.walk(listener, document);

        tokenStream.fill();
        assertEquals("service.conf", input.getSourceName());
        assertEquals("service.conf", lexer.getSourceName());
        assertEquals(12, tokenStream.getNumberOfOnChannelTokens());
        assertEquals(" ", tokenStream.getHiddenTokensToRight(0).get(0).getText());
        assertTrue(tokenStream.getHiddenTokensToRight(17).stream()
                .anyMatch(token -> token.getText().equals("# deploy target")));
        assertEquals("PROFILE", ServiceLexer.VOCABULARY.getSymbolicName(ServiceLexer.PROFILE));
        assertEquals("'{'", ServiceLexer.VOCABULARY.getDisplayName(ServiceLexer.LEFT_BRACE));
        assertEquals("production", listener.configuration.profileName);
        assertEquals(Map.of("timeout", "30", "region", "\"eu-west\""), listener.configuration.settings);
        assertTrue(listener.configuration.visitedRules.contains("profile"));
        assertTrue(listener.configuration.visitedRules.contains("setting"));
    }

    private static final class Configuration {

        private String profileName;
        private final Map<String, String> settings = new LinkedHashMap<>();
        private final Set<String> visitedRules = new LinkedHashSet<>();

        private void visited(String ruleName) {
            visitedRules.add(ruleName);
        }
    }

    private static final class ConfigurationListener implements ParseTreeListener {

        private final Configuration configuration = new Configuration();

        @Override
        public void visitTerminal(TerminalNode node) {
        }

        @Override
        public void visitErrorNode(ErrorNode node) {
            throw new IllegalStateException("Unexpected token: " + node.getText());
        }

        @Override
        public void enterEveryRule(ParserRuleContext context) {
            String ruleName = switch (context.getRuleIndex()) {
                case 1 -> "profile";
                case 2 -> "settings";
                case 3 -> "setting";
                default -> "document";
            };
            configuration.visited(ruleName);
        }

        @Override
        public void exitEveryRule(ParserRuleContext context) {
            if (context instanceof ServiceParser.ProfileContext profile) {
                configuration.profileName = profile.name.getText().replace("\"", "");
            } else if (context instanceof ServiceParser.SettingContext setting) {
                configuration.settings.put(setting.key.getText(), setting.value.getText());
            }
        }
    }

    private static final class ServiceLexer extends Lexer {

        private static final int PROFILE = 1;
        private static final int STRING = 2;
        private static final int IDENTIFIER = 3;
        private static final int NUMBER = 4;
        private static final int LEFT_BRACE = 5;
        private static final int RIGHT_BRACE = 6;
        private static final int EQUALS = 7;
        private static final int COMMA = 8;
        private static final int WS = 9;
        private static final int COMMENT = 10;

        private static final String[] LITERAL_NAMES = {
                null, null, null, null, null, "'{'", "'}'", "'='", "','"
        };
        private static final String[] SYMBOLIC_NAMES = {
                null, "PROFILE", "STRING", "IDENTIFIER", "NUMBER", "LEFT_BRACE", "RIGHT_BRACE",
                "EQUALS", "COMMA", "WS", "COMMENT"
        };
        private static final Vocabulary VOCABULARY = new VocabularyImpl(LITERAL_NAMES, SYMBOLIC_NAMES);
        private static final String[] RULE_NAMES = {
                "PROFILE", "STRING", "IDENTIFIER", "NUMBER", "LEFT_BRACE", "RIGHT_BRACE", "EQUALS", "COMMA",
                "WS", "COMMENT"
        };

        private int currentLine = 1;
        private int currentCharPositionInLine;

        private ServiceLexer(CharStream input) {
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
            int current = _input.LA(1);
            if (Character.isWhitespace(current)) {
                return emitToken(WS, Token.HIDDEN_CHANNEL, consumeWhile(Character::isWhitespace), startIndex, startLine,
                        startPosition);
            }
            if (current == '#') {
                return emitToken(COMMENT, Token.HIDDEN_CHANNEL, consumeWhile(value -> value != '\n' && value != '\r'),
                        startIndex, startLine, startPosition);
            }
            if (current == '"') {
                return emitToken(STRING, Token.DEFAULT_CHANNEL, consumeString(), startIndex, startLine, startPosition);
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
            if (Character.isDigit(current)) {
                return emitToken(NUMBER, Token.DEFAULT_CHANNEL, consumeWhile(Character::isDigit), startIndex, startLine,
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
            return "ServiceLexer";
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
                if (current == '\\' && _input.LA(1) != IntStream.EOF) {
                    text.append((char) consumeCurrentCharacter());
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
            return getTokenFactory().create(_tokenFactorySourcePair, type, text, channel, startIndex,
                    getCharIndex() - 1, line, startPosition);
        }
    }

    private static final class ServiceParser extends Parser {

        private static final String[] RULE_NAMES = {"document", "profile", "settings", "setting"};

        private ServiceParser(TokenStream input) {
            super(input);
        }

        @Override
        public String getGrammarFileName() {
            return "ServiceParser";
        }

        @Override
        public String[] getTokenNames() {
            return ServiceLexer.SYMBOLIC_NAMES;
        }

        @Override
        public Vocabulary getVocabulary() {
            return ServiceLexer.VOCABULARY;
        }

        @Override
        public String[] getRuleNames() {
            return RULE_NAMES;
        }

        @Override
        public ATN getATN() {
            return null;
        }

        private DocumentContext document() {
            DocumentContext context = new DocumentContext(_ctx, getState());
            enterRule(context, 0, 0);
            try {
                enterOuterAlt(context, 1);
                profile();
                match(Token.EOF);
                return context;
            } finally {
                exitRule();
            }
        }

        private ProfileContext profile() {
            ProfileContext context = new ProfileContext(_ctx, getState());
            enterRule(context, 1, 1);
            try {
                enterOuterAlt(context, 1);
                match(ServiceLexer.PROFILE);
                context.name = match(ServiceLexer.STRING);
                match(ServiceLexer.LEFT_BRACE);
                settings();
                match(ServiceLexer.RIGHT_BRACE);
                return context;
            } finally {
                exitRule();
            }
        }

        private void settings() {
            SettingsContext context = new SettingsContext(_ctx, getState());
            enterRule(context, 2, 2);
            try {
                enterOuterAlt(context, 1);
                setting();
                while (getInputStream().LA(1) == ServiceLexer.COMMA) {
                    match(ServiceLexer.COMMA);
                    setting();
                }
            } finally {
                exitRule();
            }
        }

        private SettingContext setting() {
            SettingContext context = new SettingContext(_ctx, getState());
            enterRule(context, 3, 3);
            try {
                enterOuterAlt(context, 1);
                context.key = match(ServiceLexer.IDENTIFIER);
                match(ServiceLexer.EQUALS);
                int valueType = getInputStream().LA(1);
                if (valueType != ServiceLexer.STRING && valueType != ServiceLexer.NUMBER) {
                    throw new IllegalArgumentException("Expected a setting value");
                }
                context.value = consume();
                return context;
            } finally {
                exitRule();
            }
        }

        private static final class DocumentContext extends ParserRuleContext {

            private DocumentContext(ParserRuleContext parent, int invokingState) {
                super(parent, invokingState);
            }

            @Override
            public int getRuleIndex() {
                return 0;
            }
        }

        private static final class ProfileContext extends ParserRuleContext {

            private Token name;

            private ProfileContext(ParserRuleContext parent, int invokingState) {
                super(parent, invokingState);
            }

            @Override
            public int getRuleIndex() {
                return 1;
            }
        }

        private static final class SettingsContext extends ParserRuleContext {

            private SettingsContext(ParserRuleContext parent, int invokingState) {
                super(parent, invokingState);
            }

            @Override
            public int getRuleIndex() {
                return 2;
            }
        }

        private static final class SettingContext extends ParserRuleContext {

            private Token key;
            private Token value;

            private SettingContext(ParserRuleContext parent, int invokingState) {
                super(parent, invokingState);
            }

            @Override
            public int getRuleIndex() {
                return 3;
            }
        }
    }
}
