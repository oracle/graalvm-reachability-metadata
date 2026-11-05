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
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ANTLR's parse-tree visitor subsystem to compile a deployment plan.
public class DeploymentPlanVisitorTest {

    @Test
    @Timeout(60)
    public void compilesDeploymentPlanFromConfiguration() {
        String source = "deploy checkout to us-east with replicas 3;\n"
                + "deploy catalog to eu-west with replicas 2;";
        CharStream input = CharStreams.fromString(source, "deployments.conf");
        DeploymentLexer lexer = new DeploymentLexer(input);
        DeploymentParser parser = new DeploymentParser(new CommonTokenStream(lexer));

        DeploymentPlan plan = new DeploymentVisitor().visit(parser.document());

        assertEquals(List.of(
                new Deployment("checkout", "us-east", 3),
                new Deployment("catalog", "eu-west", 2)), plan.deployments());
        assertEquals(3, plan.replicasFor("checkout"));
        assertEquals(5, plan.totalReplicas());
        assertEquals(0, parser.getNumberOfSyntaxErrors());
        assertEquals("deployments.conf", input.getSourceName());
    }

    private record Deployment(String service, String region, int replicas) {
    }

    private static final class DeploymentPlan {

        private final List<Deployment> deployments;

        private DeploymentPlan(List<Deployment> deployments) {
            this.deployments = List.copyOf(deployments);
        }

        private List<Deployment> deployments() {
            return deployments;
        }

        private int replicasFor(String service) {
            return deployments.stream()
                    .filter(deployment -> deployment.service().equals(service))
                    .mapToInt(Deployment::replicas)
                    .findFirst()
                    .orElseThrow();
        }

        private int totalReplicas() {
            return deployments.stream().mapToInt(Deployment::replicas).sum();
        }
    }

    private static final class DeploymentVisitor extends AbstractParseTreeVisitor<DeploymentPlan> {

        @Override
        public DeploymentPlan visitChildren(RuleNode node) {
            if (node instanceof DeploymentParser.DeploymentContext deployment) {
                return new DeploymentPlan(List.of(new Deployment(
                        deployment.service.getText(), deployment.region.getText(),
                        Integer.parseInt(deployment.replicas.getText()))));
            }
            return super.visitChildren(node);
        }

        @Override
        protected DeploymentPlan defaultResult() {
            return new DeploymentPlan(List.of());
        }

        @Override
        protected DeploymentPlan aggregateResult(DeploymentPlan aggregate, DeploymentPlan nextResult) {
            List<Deployment> deployments = new ArrayList<>(aggregate.deployments());
            deployments.addAll(nextResult.deployments());
            return new DeploymentPlan(deployments);
        }
    }

    private static final class DeploymentLexer extends Lexer {

        private static final int DEPLOY = 1;
        private static final int WORD = 2;
        private static final int TO = 3;
        private static final int WITH = 4;
        private static final int REPLICAS = 5;
        private static final int NUMBER = 6;
        private static final int SEMICOLON = 7;
        private static final int WS = 8;
        private static final String[] LITERAL_NAMES = {
                null, null, null, null, null, null, null, "';'"
        };
        private static final String[] SYMBOLIC_NAMES = {
                null, "DEPLOY", "WORD", "TO", "WITH", "REPLICAS", "NUMBER", "SEMICOLON", "WS"
        };
        private static final Vocabulary VOCABULARY = new VocabularyImpl(LITERAL_NAMES, SYMBOLIC_NAMES);
        private static final String[] RULE_NAMES = {
                "DEPLOY", "WORD", "TO", "WITH", "REPLICAS", "NUMBER", "SEMICOLON", "WS"
        };

        private int currentLine = 1;
        private int currentCharPositionInLine;

        private DeploymentLexer(CharStream input) {
            super(input);
        }

        @Override
        public Token nextToken() {
            while (_input.LA(1) != IntStream.EOF && Character.isWhitespace(_input.LA(1))) {
                consumeCurrentCharacter();
            }
            if (_input.LA(1) == IntStream.EOF) {
                return emitEOF();
            }

            int startIndex = getCharIndex();
            int startLine = getLine();
            int startPosition = getCharPositionInLine();
            int current = _input.LA(1);
            if (current == ';') {
                consumeCurrentCharacter();
                return emitToken(SEMICOLON, ";", startIndex, startLine, startPosition);
            }
            if (Character.isDigit(current)) {
                return emitToken(NUMBER, consumeWhile(Character::isDigit), startIndex, startLine, startPosition);
            }
            if (Character.isLetter(current)) {
                String text = consumeWhile(value -> Character.isLetterOrDigit(value) || value == '-');
                int type = switch (text) {
                    case "deploy" -> DEPLOY;
                    case "to" -> TO;
                    case "with" -> WITH;
                    case "replicas" -> REPLICAS;
                    default -> WORD;
                };
                return emitToken(type, text, startIndex, startLine, startPosition);
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
            return "DeploymentLexer";
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
            return new String[]{"DEFAULT_TOKEN_CHANNEL"};
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

        private Token emitToken(int type, String text, int startIndex, int line, int startPosition) {
            TokenFactory<?> tokenFactory = getTokenFactory();
            return tokenFactory.create(_tokenFactorySourcePair, type, text, Token.DEFAULT_CHANNEL, startIndex,
                    getCharIndex() - 1, line, startPosition);
        }
    }

    private static final class DeploymentParser extends Parser {

        private static final String[] RULE_NAMES = {"document", "deployment"};

        private DeploymentParser(TokenStream input) {
            super(input);
        }

        private DocumentContext document() {
            DocumentContext context = new DocumentContext(_ctx, getState());
            enterRule(context, 0, 0);
            try {
                enterOuterAlt(context, 1);
                while (getInputStream().LA(1) == DeploymentLexer.DEPLOY) {
                    deployment();
                }
                match(Token.EOF);
                return context;
            } finally {
                exitRule();
            }
        }

        private DeploymentContext deployment() {
            DeploymentContext context = new DeploymentContext(_ctx, getState());
            enterRule(context, 1, 1);
            try {
                enterOuterAlt(context, 1);
                match(DeploymentLexer.DEPLOY);
                context.service = match(DeploymentLexer.WORD);
                match(DeploymentLexer.TO);
                context.region = match(DeploymentLexer.WORD);
                match(DeploymentLexer.WITH);
                match(DeploymentLexer.REPLICAS);
                context.replicas = match(DeploymentLexer.NUMBER);
                match(DeploymentLexer.SEMICOLON);
                return context;
            } finally {
                exitRule();
            }
        }

        @Override
        public String getGrammarFileName() {
            return "DeploymentParser";
        }

        @Override
        public String[] getTokenNames() {
            return DeploymentLexer.SYMBOLIC_NAMES;
        }

        @Override
        public Vocabulary getVocabulary() {
            return DeploymentLexer.VOCABULARY;
        }

        @Override
        public String[] getRuleNames() {
            return RULE_NAMES;
        }

        @Override
        public int getTokenType(String tokenName) {
            return switch (tokenName) {
                case "DEPLOY" -> DeploymentLexer.DEPLOY;
                case "WORD" -> DeploymentLexer.WORD;
                case "TO" -> DeploymentLexer.TO;
                case "WITH" -> DeploymentLexer.WITH;
                case "REPLICAS" -> DeploymentLexer.REPLICAS;
                case "NUMBER" -> DeploymentLexer.NUMBER;
                case "SEMICOLON" -> DeploymentLexer.SEMICOLON;
                default -> Token.INVALID_TYPE;
            };
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

        private static final class DeploymentContext extends ParserRuleContext {

            private Token service;
            private Token region;
            private Token replicas;

            private DeploymentContext(ParserRuleContext parent, int invokingState) {
                super(parent, invokingState);
            }

            @Override
            public int getRuleIndex() {
                return 1;
            }
        }
    }
}
