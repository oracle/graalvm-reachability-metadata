/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.antlr4_runtime;

import org.antlr.v4.runtime.BailErrorStrategy;
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
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises ANTLR's fail-fast parser error-strategy subsystem for route validation.
public class FailFastParsingTest {

    @Test
    @Timeout(60)
    public void acceptsCompleteRouteAndRejectsIncompleteRoute() {
        RouteParser validParser = parserFor("route checkout; route catalog;");
        RouteParser.DocumentContext document = validParser.document();
        assertEquals(List.of("checkout", "catalog"), document.routeNames);
        assertEquals(0, validParser.getNumberOfSyntaxErrors());

        RouteParser invalidParser = parserFor("route checkout route catalog;");
        invalidParser.setErrorHandler(new BailErrorStrategy());
        assertThrows(ParseCancellationException.class, invalidParser::document);
        assertEquals(0, invalidParser.getNumberOfSyntaxErrors());
        assertEquals("route", invalidParser.getCurrentToken().getText());
    }

    private static RouteParser parserFor(String source) {
        CharStream input = CharStreams.fromString(source, "routes.conf");
        return new RouteParser(new CommonTokenStream(new RouteLexer(input)));
    }

    private static final class RouteLexer extends Lexer {

        private static final int ROUTE = 1;
        private static final int WORD = 2;
        private static final int SEMICOLON = 3;
        private static final int WS = 4;
        private static final String[] LITERAL_NAMES = {null, null, null, "';'"};
        private static final String[] SYMBOLIC_NAMES = {null, "ROUTE", "WORD", "SEMICOLON", "WS"};
        private static final Vocabulary VOCABULARY = new VocabularyImpl(LITERAL_NAMES, SYMBOLIC_NAMES);
        private static final String[] RULE_NAMES = {"ROUTE", "WORD", "SEMICOLON", "WS"};

        private int currentLine = 1;
        private int currentCharPositionInLine;

        private RouteLexer(CharStream input) {
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
            if (current == ';') {
                consumeCurrentCharacter();
                return emitToken(SEMICOLON, Token.DEFAULT_CHANNEL, ";", startIndex, startLine, startPosition);
            }
            if (Character.isLetter(current)) {
                String text = consumeWhile(Character::isLetterOrDigit);
                int type = text.equals("route") ? ROUTE : WORD;
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
            return "RouteLexer";
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

    private static final class RouteParser extends Parser {

        private static final String[] RULE_NAMES = {"document", "route"};
        private static final Vocabulary VOCABULARY = RouteLexer.VOCABULARY;

        private RouteParser(TokenStream input) {
            super(input);
        }

        @Override
        public String getGrammarFileName() {
            return "RouteParser";
        }

        @Override
        public String[] getTokenNames() {
            return RouteLexer.SYMBOLIC_NAMES;
        }

        @Override
        public Vocabulary getVocabulary() {
            return VOCABULARY;
        }

        @Override
        public String[] getRuleNames() {
            return RULE_NAMES;
        }

        @Override
        public int getTokenType(String tokenName) {
            return switch (tokenName) {
                case "ROUTE" -> RouteLexer.ROUTE;
                case "WORD" -> RouteLexer.WORD;
                case "SEMICOLON" -> RouteLexer.SEMICOLON;
                default -> Token.INVALID_TYPE;
            };
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
                while (getInputStream().LA(1) == RouteLexer.ROUTE) {
                    context.routeNames.add(route());
                }
                match(Token.EOF);
                return context;
            } finally {
                exitRule();
            }
        }

        private String route() {
            ParserRuleContext context = new ParserRuleContext(_ctx, getState());
            enterRule(context, 1, 1);
            try {
                enterOuterAlt(context, 1);
                match(RouteLexer.ROUTE);
                String name = match(RouteLexer.WORD).getText();
                match(RouteLexer.SEMICOLON);
                return name;
            } finally {
                exitRule();
            }
        }

        private static final class DocumentContext extends ParserRuleContext {

            private final List<String> routeNames = new ArrayList<>();

            private DocumentContext(ParserRuleContext parent, int invokingState) {
                super(parent, invokingState);
            }

            @Override
            public int getRuleIndex() {
                return 0;
            }
        }
    }
}
