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
import org.antlr.v4.runtime.ListTokenSource;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.UnbufferedTokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Trees;
import org.antlr.v4.runtime.tree.xpath.XPath;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class Antlr4_runtimeTest {

    @Test
    void lexerHandlesUnicodeCommentsAndVocabulary() throws IOException {
        CharStream input = CharStreams.fromReader(new StringReader("name = \"h\u00E9llo\" + beta42#tail"), "assignment-fixture");
        AssignmentLexer lexer = new AssignmentLexer(input);

        Vocabulary vocabularyFromTokenNames = VocabularyImpl.fromTokenNames(AssignmentLexer.TOKEN_NAMES);
        List<? extends Token> tokens = lexer.getAllTokens();
        List<? extends Token> defaultChannelTokens = tokens.stream()
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .collect(Collectors.toList());
        List<? extends Token> hiddenTokens = tokens.stream()
                .filter(token -> token.getChannel() == Token.HIDDEN_CHANNEL)
                .collect(Collectors.toList());

        assertThat(input.getSourceName()).isEqualTo("assignment-fixture");
        assertThat(lexer.getSourceName()).isEqualTo("assignment-fixture");
        assertThat(vocabularyFromTokenNames.getSymbolicName(AssignmentLexer.IDENTIFIER)).isEqualTo("IDENTIFIER");
        assertThat(AssignmentLexer.VOCABULARY.getDisplayName(AssignmentLexer.EQUALS)).isEqualTo("'='");
        assertThat(defaultChannelTokens)
                .extracting(Token::getText)
                .containsExactly("name", "=", "\"h\u00E9llo\"", "+", "beta42");
        assertThat(defaultChannelTokens)
                .extracting(Token::getType)
                .containsExactly(
                        AssignmentLexer.IDENTIFIER,
                        AssignmentLexer.EQUALS,
                        AssignmentLexer.STRING,
                        AssignmentLexer.PLUS,
                        AssignmentLexer.IDENTIFIER);
        assertThat(hiddenTokens)
                .extracting(Token::getText)
                .containsExactly(" ", " ", " ", " ", "#tail");
        assertThat(hiddenTokens)
                .extracting(Token::getChannel)
                .containsOnly(Token.HIDDEN_CHANNEL);
    }

    @Test
    void parserBuildsTreeThatCanBeWalkedAndQueried() {
        String input = "total = \"h\u00E9llo\" + count + 42";
        CommonTokenStream tokenStream = new CommonTokenStream(new AssignmentLexer(CharStreams.fromString(input, "tree-fixture")));
        AssignmentParser parser = new AssignmentParser(tokenStream);

        AssignmentParser.DocumentContext document = parser.document();
        AssignmentParser.AssignmentContext assignment = document.getRuleContext(AssignmentParser.AssignmentContext.class, 0);
        AssignmentParser.ValueContext secondValue = assignment.getRuleContext(AssignmentParser.ValueContext.class, 1);
        RecordingParseTreeListener listener = new RecordingParseTreeListener(parser);

        ParseTreeWalker.DEFAULT.walk(listener, document);
        Collection<ParseTree> valueNodes = XPath.findAll(document, "//value", parser);
        Collection<ParseTree> identifierNodes = Trees.findAllTokenNodes(document, AssignmentLexer.IDENTIFIER);
        ParserRuleContext enclosingSubtree = Trees.getRootOfSubtreeEnclosingRegion(
                document,
                secondValue.getStart().getTokenIndex(),
                secondValue.getStop().getTokenIndex());

        assertThat(Trees.toStringTree(document, parser))
                .isEqualTo("(document (assignment total = (value \"h\u00E9llo\") + (value count) + (value 42)) )");
        assertThat(listener.enteredRules).containsExactly("document", "assignment", "value", "value", "value");
        assertThat(listener.visitedTerminals)
                .containsExactly("total", "=", "\"h\u00E9llo\"", "+", "count", "+", "42", "");
        assertThat(valueNodes).hasSize(3);
        assertThat(identifierNodes)
                .extracting(ParseTree::getText)
                .containsExactly("total", "count");
        assertThat(Trees.getAncestors(secondValue))
                .extracting(node -> Trees.getNodeText(node, parser))
                .containsExactly("document", "assignment");
        assertThat(enclosingSubtree).isSameAs(secondValue);
        assertThat(parser.getRuleInvocationStack(secondValue)).containsExactly("value", "assignment", "document");
    }

    @Test
    void tokenStreamsCanBeRewrittenAndFilteredByIntervals() {
        String input = "result = alpha + 42";
        AssignmentLexer lexer = new AssignmentLexer(CharStreams.fromString(input, "rewriter-fixture"));
        List<Token> capturedTokens = new ArrayList<>(lexer.getAllTokens());
        ListTokenSource tokenSource = new ListTokenSource(capturedTokens, "rewriter-fixture");
        CommonTokenStream tokenStream = new CommonTokenStream(tokenSource);
        TokenStreamRewriter rewriter = new TokenStreamRewriter(tokenStream);

        tokenStream.fill();

        Token assignmentTarget = tokenStream.get(4);
        Token numberLiteral = tokenStream.get(8);
        IntervalSet valueTypes = IntervalSet.of(AssignmentLexer.IDENTIFIER, AssignmentLexer.STRING);
        valueTypes.add(AssignmentLexer.NUMBER);
        IntervalSet operatorTypes = new IntervalSet(AssignmentLexer.EQUALS, AssignmentLexer.PLUS);
        IntervalSet allVisibleTypes = valueTypes.or(operatorTypes);
        IntervalSet nonNumericValueTypes = valueTypes.subtract(IntervalSet.of(AssignmentLexer.NUMBER));

        rewriter.insertBefore(assignmentTarget, "prefix + ");
        rewriter.replace(numberLiteral, "100");
        rewriter.insertAfter("audit", numberLiteral, " /*checked*/");

        assertThat(tokenSource.getSourceName()).isEqualTo("rewriter-fixture");
        assertThat(tokenStream.getSourceName()).isEqualTo("rewriter-fixture");
        assertThat(tokenStream.getNumberOfOnChannelTokens()).isEqualTo(6);
        assertThat(tokenStream.getHiddenTokensToRight(0))
                .extracting(Token::getText)
                .containsExactly(" ");
        assertThat(tokenStream.getHiddenTokensToLeft(2))
                .extracting(Token::getText)
                .containsExactly(" ");
        assertThat(rewriter.getText()).isEqualTo("result = prefix + alpha + 100");
        assertThat(rewriter.getText("audit")).isEqualTo("result = alpha + 42 /*checked*/");
        assertThat(nonNumericValueTypes.toList())
                .containsExactly(AssignmentLexer.IDENTIFIER, AssignmentLexer.STRING);
        assertThat(allVisibleTypes.toString(AssignmentLexer.VOCABULARY))
                .contains("IDENTIFIER")
                .contains("STRING")
                .contains("'='")
                .contains("'+'");
    }

    @Test
    void unbufferedTokenStreamsSupportMarksLookaheadAndTextExtraction() {
        AssignmentLexer lexer = new AssignmentLexer(CharStreams.fromString("delta = 7 + 9", "stream-fixture"));
        UnbufferedTokenStream<Token> tokenStream = new UnbufferedTokenStream<>(lexer, 2);

        int marker = tokenStream.mark();
        Token identifier = tokenStream.LT(1);

        tokenStream.consume();
        Token whitespace = tokenStream.LT(1);
        tokenStream.consume();
        Token equals = tokenStream.LT(1);

        assertThat(tokenStream.getSourceName()).isEqualTo("stream-fixture");
        assertThat(identifier.getText()).isEqualTo("delta");
        assertThat(whitespace.getText()).isEqualTo(" ");
        assertThat(whitespace.getChannel()).isEqualTo(Token.HIDDEN_CHANNEL);
        assertThat(equals.getType()).isEqualTo(AssignmentLexer.EQUALS);
        assertThat(tokenStream.getText(identifier, equals)).isEqualTo("delta =");

        tokenStream.seek(0);

        assertThat(tokenStream.LT(1).getText()).isEqualTo("delta");
        assertThat(tokenStream.LA(3)).isEqualTo(AssignmentLexer.EQUALS);

        tokenStream.release(marker);
    }

    @Test
    void parseTreeVisitorsCanShortCircuitAndStoreRuleAnnotations() {
        CommonTokenStream tokenStream = new CommonTokenStream(
                new AssignmentLexer(CharStreams.fromString("result = alpha + 42 + 99", "visitor-fixture")));
        AssignmentParser parser = new AssignmentParser(tokenStream);
        AssignmentParser.DocumentContext document = parser.document();
        AssignmentParser.AssignmentContext assignment = document.getRuleContext(AssignmentParser.AssignmentContext.class, 0);
        AssignmentParser.ValueContext firstValue = assignment.getRuleContext(AssignmentParser.ValueContext.class, 0);
        AssignmentParser.ValueContext secondValue = assignment.getRuleContext(AssignmentParser.ValueContext.class, 1);
        AssignmentParser.ValueContext thirdValue = assignment.getRuleContext(AssignmentParser.ValueContext.class, 2);
        FirstNumericValueVisitor visitor = new FirstNumericValueVisitor();

        String firstNumericLiteral = visitor.visit(document);

        assertThat(firstNumericLiteral).isEqualTo("42");
        assertThat(visitor.getVisitedTerminals()).containsExactly("result", "=", "alpha", "+", "42");
        assertThat(visitor.getVisitedValueTexts().get(firstValue)).isEqualTo("alpha");
        assertThat(visitor.getVisitedValueTexts().get(secondValue)).isEqualTo("42");
        assertThat(visitor.getVisitedValueTexts().get(thirdValue)).isNull();
        assertThat(visitor.getVisitedValueTexts().removeFrom(secondValue)).isEqualTo("42");
        assertThat(visitor.getVisitedValueTexts().get(secondValue)).isNull();
    }

    private static final class RecordingParseTreeListener implements ParseTreeListener {

        private final Parser parser;
        private final List<String> enteredRules = new ArrayList<>();
        private final List<String> visitedTerminals = new ArrayList<>();

        private RecordingParseTreeListener(Parser parser) {
            this.parser = parser;
        }

        @Override
        public void visitTerminal(TerminalNode node) {
            visitedTerminals.add(node.getText());
        }

        @Override
        public void visitErrorNode(org.antlr.v4.runtime.tree.ErrorNode node) {
            visitedTerminals.add(node.getText());
        }

        @Override
        public void enterEveryRule(ParserRuleContext context) {
            enteredRules.add(parser.getRuleNames()[context.getRuleIndex()]);
        }

        @Override
        public void exitEveryRule(ParserRuleContext context) {
        }
    }

    private static final class FirstNumericValueVisitor extends AbstractParseTreeVisitor<String> {

        private final ParseTreeProperty<String> visitedValueTexts = new ParseTreeProperty<>();
        private final List<String> visitedTerminals = new ArrayList<>();

        private ParseTreeProperty<String> getVisitedValueTexts() {
            return visitedValueTexts;
        }

        private List<String> getVisitedTerminals() {
            return visitedTerminals;
        }

        @Override
        public String visitChildren(RuleNode node) {
            String result = super.visitChildren(node);
            if (node instanceof AssignmentParser.ValueContext valueContext) {
                visitedValueTexts.put(valueContext, valueContext.getText());
                if (result == null && valueContext.getStart().getType() == AssignmentLexer.NUMBER) {
                    return valueContext.getText();
                }
            }
            return result;
        }

        @Override
        public String visitTerminal(TerminalNode node) {
            visitedTerminals.add(node.getText());
            if (node.getSymbol().getType() == AssignmentLexer.NUMBER) {
                return node.getText();
            }
            return null;
        }

        @Override
        protected String aggregateResult(String aggregate, String nextResult) {
            return aggregate != null ? aggregate : nextResult;
        }

        @Override
        protected boolean shouldVisitNextChild(RuleNode node, String currentResult) {
            return currentResult == null;
        }
    }

    private static final class AssignmentLexer extends Lexer {

        private static final int STRING_MODE = 1;

        private static final String[] LITERAL_NAMES = {null, null, null, null, "'='", "'+'"};
        private static final String[] SYMBOLIC_NAMES = {
                null,
                "IDENTIFIER",
                "NUMBER",
                "STRING",
                "EQUALS",
                "PLUS",
                "WS",
                "COMMENT"
        };
        private static final String[] MODE_NAMES = {"DEFAULT_MODE", "STRING_MODE"};
        private static final String[] RULE_NAMES = {
                "IDENTIFIER",
                "NUMBER",
                "STRING",
                "EQUALS",
                "PLUS",
                "WS",
                "COMMENT"
        };
        private static final Vocabulary VOCABULARY = new VocabularyImpl(LITERAL_NAMES, SYMBOLIC_NAMES);
        private static final String[] TOKEN_NAMES = buildTokenNames();

        private static final int IDENTIFIER = 1;
        private static final int NUMBER = 2;
        private static final int STRING = 3;
        private static final int EQUALS = 4;
        private static final int PLUS = 5;
        private static final int WS = 6;
        private static final int COMMENT = 7;

        private int currentLine = 1;
        private int currentCharPositionInLine;

        private AssignmentLexer(CharStream input) {
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
            int startCharPositionInLine = getCharPositionInLine();
            int current = _input.LA(1);

            if (Character.isWhitespace(current)) {
                String text = consumeWhile(Character::isWhitespace);
                return emitToken(WS, Token.HIDDEN_CHANNEL, text, startIndex, startLine, startCharPositionInLine);
            }
            if (current == '#') {
                String text = consumeComment();
                return emitToken(COMMENT, Token.HIDDEN_CHANNEL, text, startIndex, startLine, startCharPositionInLine);
            }
            if (current == '=') {
                consumeCurrentCharacter();
                return emitToken(EQUALS, Token.DEFAULT_CHANNEL, "=", startIndex, startLine, startCharPositionInLine);
            }
            if (current == '+') {
                consumeCurrentCharacter();
                return emitToken(PLUS, Token.DEFAULT_CHANNEL, "+", startIndex, startLine, startCharPositionInLine);
            }
            if (current == '"') {
                String text = consumeStringLiteral();
                return emitToken(STRING, Token.DEFAULT_CHANNEL, text, startIndex, startLine, startCharPositionInLine);
            }
            if (Character.isLetter(current) || current == '_') {
                String text = consumeWhile(ch -> Character.isLetterOrDigit(ch) || ch == '_');
                return emitToken(IDENTIFIER, Token.DEFAULT_CHANNEL, text, startIndex, startLine, startCharPositionInLine);
            }
            if (Character.isDigit(current)) {
                String text = consumeWhile(Character::isDigit);
                return emitToken(NUMBER, Token.DEFAULT_CHANNEL, text, startIndex, startLine, startCharPositionInLine);
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
            return "AssignmentLexer";
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
            return TOKEN_NAMES;
        }

        @Override
        public ATN getATN() {
            return null;
        }

        private Token emitToken(int type, int channel, String text, int startIndex, int line, int charPositionInLine) {
            return getTokenFactory().create(
                    _tokenFactorySourcePair,
                    type,
                    text,
                    channel,
                    startIndex,
                    getCharIndex() - 1,
                    line,
                    charPositionInLine);
        }

        private String consumeWhile(IntPredicate predicate) {
            StringBuilder builder = new StringBuilder();
            while (_input.LA(1) != IntStream.EOF && predicate.test(_input.LA(1))) {
                builder.append((char) consumeCurrentCharacter());
            }
            return builder.toString();
        }

        private String consumeComment() {
            StringBuilder builder = new StringBuilder();
            while (_input.LA(1) != IntStream.EOF && _input.LA(1) != '\n' && _input.LA(1) != '\r') {
                builder.append((char) consumeCurrentCharacter());
            }
            return builder.toString();
        }

        private String consumeStringLiteral() {
            StringBuilder builder = new StringBuilder();
            pushMode(STRING_MODE);
            builder.append((char) consumeCurrentCharacter());
            while (_input.LA(1) != IntStream.EOF) {
                int current = consumeCurrentCharacter();
                builder.append((char) current);
                if (current == '\\' && _input.LA(1) != IntStream.EOF) {
                    builder.append((char) consumeCurrentCharacter());
                    continue;
                }
                if (current == '"') {
                    popMode();
                    return builder.toString();
                }
            }
            throw new IllegalArgumentException("Unterminated string literal");
        }

        private int consumeCurrentCharacter() {
            int current = _input.LA(1);
            _input.consume();
            if (current == '\n') {
                setLine(getLine() + 1);
                setCharPositionInLine(0);
            } else {
                setCharPositionInLine(getCharPositionInLine() + 1);
            }
            return current;
        }

        private static String[] buildTokenNames() {
            String[] tokenNames = new String[SYMBOLIC_NAMES.length];
            for (int i = 0; i < tokenNames.length; i++) {
                String literalName = VOCABULARY.getLiteralName(i);
                String symbolicName = VOCABULARY.getSymbolicName(i);
                tokenNames[i] = literalName != null ? literalName : symbolicName;
            }
            return tokenNames;
        }

        @FunctionalInterface
        private interface IntPredicate {
            boolean test(int value);
        }
    }

    private static final class AssignmentParser extends Parser {

        private static final int RULE_document = 0;
        private static final int RULE_assignment = 1;
        private static final int RULE_value = 2;

        private static final String[] RULE_NAMES = {"document", "assignment", "value"};

        private AssignmentParser(TokenStream input) {
            super(input);
        }

        @Override
        public String getGrammarFileName() {
            return "AssignmentParser";
        }

        @Override
        public String[] getTokenNames() {
            return AssignmentLexer.TOKEN_NAMES;
        }

        @Override
        public Vocabulary getVocabulary() {
            return AssignmentLexer.VOCABULARY;
        }

        @Override
        public String[] getRuleNames() {
            return RULE_NAMES;
        }

        @Override
        public int getTokenType(String tokenName) {
            return switch (tokenName) {
                case "IDENTIFIER" -> AssignmentLexer.IDENTIFIER;
                case "NUMBER" -> AssignmentLexer.NUMBER;
                case "STRING" -> AssignmentLexer.STRING;
                case "EQUALS", "='", "'='" -> AssignmentLexer.EQUALS;
                case "PLUS", "+", "'+'" -> AssignmentLexer.PLUS;
                default -> Token.INVALID_TYPE;
            };
        }

        @Override
        public int getRuleIndex(String ruleName) {
            return switch (ruleName) {
                case "document" -> RULE_document;
                case "assignment" -> RULE_assignment;
                case "value" -> RULE_value;
                default -> -1;
            };
        }

        @Override
        public ATN getATN() {
            return null;
        }

        private DocumentContext document() {
            DocumentContext context = new DocumentContext(_ctx, getState());
            enterRule(context, 0, RULE_document);
            try {
                enterOuterAlt(context, 1);
                assignment();
                match(Token.EOF);
                return context;
            } finally {
                exitRule();
            }
        }

        private AssignmentContext assignment() {
            AssignmentContext context = new AssignmentContext(_ctx, getState());
            enterRule(context, 1, RULE_assignment);
            try {
                enterOuterAlt(context, 1);
                match(AssignmentLexer.IDENTIFIER);
                match(AssignmentLexer.EQUALS);
                value();
                while (getInputStream().LA(1) == AssignmentLexer.PLUS) {
                    match(AssignmentLexer.PLUS);
                    value();
                }
                return context;
            } finally {
                exitRule();
            }
        }

        private ValueContext value() {
            ValueContext context = new ValueContext(_ctx, getState());
            enterRule(context, 2, RULE_value);
            try {
                enterOuterAlt(context, 1);
                int lookahead = getInputStream().LA(1);
                if (lookahead == AssignmentLexer.IDENTIFIER || lookahead == AssignmentLexer.NUMBER || lookahead == AssignmentLexer.STRING) {
                    consume();
                    return context;
                }
                throw new IllegalStateException("Unexpected token type: " + lookahead);
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
                return RULE_document;
            }
        }

        private static final class AssignmentContext extends ParserRuleContext {

            private AssignmentContext(ParserRuleContext parent, int invokingState) {
                super(parent, invokingState);
            }

            @Override
            public int getRuleIndex() {
                return RULE_assignment;
            }
        }

        private static final class ValueContext extends ParserRuleContext {

            private ValueContext(ParserRuleContext parent, int invokingState) {
                super(parent, invokingState);
            }

            @Override
            public int getRuleIndex() {
                return RULE_value;
            }
        }
    }
}
