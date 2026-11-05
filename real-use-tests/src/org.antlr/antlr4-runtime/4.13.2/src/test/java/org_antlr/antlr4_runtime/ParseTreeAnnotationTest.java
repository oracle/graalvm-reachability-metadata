/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.antlr4_runtime;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.Trees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collection;
import java.util.List;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Exercises ANTLR's parse-tree annotation subsystem to enrich a deployment configuration.
public class ParseTreeAnnotationTest {

    @Test
    @Timeout(60)
    public void annotatesSettingsForDeploymentAudit() {
        String source = "service checkout { timeout = 30, region = eu-west, replicas = 3 }";
        ConfigurationParser parser = parserFor(source);
        ConfigurationParser.DocumentContext document = parser.document();

        ParseTreeProperty<SettingAudit> annotations = new ParseTreeProperty<>();
        Collection<ParseTree> settingNodes = Trees.findAllRuleNodes(document, ConfigurationParser.SETTING);
        for (ParseTree settingNode : settingNodes) {
            ConfigurationParser.SettingContext setting = (ConfigurationParser.SettingContext) settingNode;
            annotations.put(setting, new SettingAudit(setting.key.getText(), setting.value.getText()));
        }

        assertEquals(List.of("timeout=30", "region=eu-west", "replicas=3"), settingNodes.stream()
                .map(annotations::get)
                .map(SettingAudit::render)
                .toList());
        ParseTree region = settingNodes.stream()
                .filter(node -> "region".equals(annotations.get(node).key()))
                .findFirst()
                .orElseThrow();
        assertEquals("region=eu-west", annotations.get(region).render());
        assertNull(annotations.get(document));
        assertEquals(3, settingNodes.size());
    }

    private static ConfigurationParser parserFor(String source) {
        CharStream input = CharStreams.fromString(source, "services.conf");
        return new ConfigurationParser(new CommonTokenStream(new ConfigurationLexer(input)));
    }

    private record SettingAudit(String key, String value) {
        private String render() {
            return key + "=" + value;
        }
    }

    private static final class ConfigurationLexer extends Lexer {

        private static final int SERVICE = 1;
        private static final int WORD = 2;
        private static final int NUMBER = 3;
        private static final int LEFT_BRACE = 4;
        private static final int RIGHT_BRACE = 5;
        private static final int EQUALS = 6;
        private static final int COMMA = 7;
        private static final int WS = 8;
        private static final String[] LITERAL_NAMES = {
                null, null, null, null, "'{'", "'}'", "'='", "','"
        };
        private static final String[] SYMBOLIC_NAMES = {
                null, "SERVICE", "WORD", "NUMBER", "LEFT_BRACE", "RIGHT_BRACE", "EQUALS", "COMMA", "WS"
        };
        private static final Vocabulary VOCABULARY = new VocabularyImpl(LITERAL_NAMES, SYMBOLIC_NAMES);
        private static final String[] RULE_NAMES = {
                "SERVICE", "WORD", "NUMBER", "LEFT_BRACE", "RIGHT_BRACE", "EQUALS", "COMMA", "WS"
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
                return emitToken(NUMBER, Token.DEFAULT_CHANNEL, consumeWhile(Character::isDigit), startIndex,
                        startLine, startPosition);
            }
            if (Character.isLetter(current)) {
                String text = consumeWhile(value -> Character.isLetterOrDigit(value) || value == '-');
                int type = text.equals("service") ? SERVICE : WORD;
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

        private static final int SERVICE = ConfigurationLexer.SERVICE;
        private static final int WORD = ConfigurationLexer.WORD;
        private static final int NUMBER = ConfigurationLexer.NUMBER;
        private static final int LEFT_BRACE = ConfigurationLexer.LEFT_BRACE;
        private static final int RIGHT_BRACE = ConfigurationLexer.RIGHT_BRACE;
        private static final int EQUALS = ConfigurationLexer.EQUALS;
        private static final int COMMA = ConfigurationLexer.COMMA;
        private static final int SETTING = 3;
        private static final String[] RULE_NAMES = {"document", "service", "settings", "setting"};

        private ConfigurationParser(TokenStream input) {
            super(input);
        }

        private DocumentContext document() {
            DocumentContext context = new DocumentContext(_ctx, getState());
            enterRule(context, 0, 0);
            try {
                enterOuterAlt(context, 1);
                service();
                match(Token.EOF);
                return context;
            } finally {
                exitRule();
            }
        }

        private void service() {
            ServiceContext context = new ServiceContext(_ctx, getState());
            enterRule(context, 1, 1);
            try {
                enterOuterAlt(context, 1);
                match(SERVICE);
                match(WORD);
                match(LEFT_BRACE);
                settings();
                match(RIGHT_BRACE);
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
                while (getInputStream().LA(1) == COMMA) {
                    match(COMMA);
                    setting();
                }
            } finally {
                exitRule();
            }
        }

        private SettingContext setting() {
            SettingContext context = new SettingContext(_ctx, getState());
            enterRule(context, SETTING, SETTING);
            try {
                enterOuterAlt(context, 1);
                context.key = match(WORD);
                match(EQUALS);
                int valueType = getInputStream().LA(1);
                if (valueType != WORD && valueType != NUMBER) {
                    throw new IllegalArgumentException("Expected a setting value");
                }
                context.value = consume();
                return context;
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
        public ATN getATN() {
            return null;
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

        private static final class ServiceContext extends ParserRuleContext {

            private ServiceContext(ParserRuleContext parent, int invokingState) {
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
                return SETTING;
            }
        }
    }
}
