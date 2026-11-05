/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.antlr4_runtime;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.UnbufferedCharStream;
import org.antlr.v4.runtime.UnbufferedTokenStream;
import org.antlr.v4.runtime.atn.ATN;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ANTLR's unbuffered character and token stream subsystems to process deployment records.
public class UnbufferedDeploymentStreamTest {

    @Test
    @Timeout(60)
    public void processesDeploymentRecordsAsAStream() {
        String source = "deploy checkout version 4.13.2\n"
                + "deploy catalog version 2.4.0\n"
                + "deploy checkout version 4.13.2\n";
        UnbufferedCharStream input = new UnbufferedCharStream(new StringReader(source), 4);
        input.name = "deployments.conf";
        DeploymentLexer lexer = new DeploymentLexer(input);
        UnbufferedTokenStream<Token> tokens = new UnbufferedTokenStream<>(lexer, 2);
        List<String> processedRecords = new ArrayList<>();
        Map<String, String> deployedVersions = new LinkedHashMap<>();

        while (tokens.LA(1) != Token.EOF) {
            if (tokens.LA(1) == DeploymentLexer.NEWLINE) {
                tokens.consume();
                continue;
            }

            int marker = tokens.mark();
            Token command = tokens.LT(1);
            tokens.consume();
            Token service = tokens.LT(1);
            tokens.consume();
            Token versionKeyword = tokens.LT(1);
            tokens.consume();
            Token version = tokens.LT(1);
            tokens.consume();
            assertEquals("deploy" + service.getText() + "version", tokens.getText(command, versionKeyword));
            assertEquals("deploy" + service.getText() + "version" + version.getText(),
                    tokens.getText(command, version));
            assertEquals("deploy", command.getText());
            assertEquals("version", versionKeyword.getText());
            assertEquals(DeploymentLexer.NEWLINE, tokens.LA(1));

            processedRecords.add(service.getText() + "@" + version.getText());
            deployedVersions.put(service.getText(), version.getText());
            tokens.consume();
            tokens.release(marker);
        }

        assertEquals("deployments.conf", input.getSourceName());
        assertEquals("deployments.conf", lexer.getSourceName());
        assertEquals("deployments.conf", tokens.getSourceName());
        assertEquals(List.of("checkout@4.13.2", "catalog@2.4.0", "checkout@4.13.2"), processedRecords);
        assertEquals(Map.of("checkout", "4.13.2", "catalog", "2.4.0"), deployedVersions);
    }

    private static final class DeploymentLexer extends Lexer {

        private static final int WORD = 1;
        private static final int NEWLINE = 2;
        private static final String[] SYMBOLIC_NAMES = {null, "WORD", "NEWLINE"};
        private static final Vocabulary VOCABULARY = new VocabularyImpl(null, SYMBOLIC_NAMES);
        private static final String[] RULE_NAMES = {"WORD", "NEWLINE"};

        private int currentLine = 1;
        private int currentCharPositionInLine;

        private DeploymentLexer(CharStream input) {
            super(input);
        }

        @Override
        public Token nextToken() {
            while (_input.LA(1) != IntStream.EOF && Character.isWhitespace(_input.LA(1))
                    && _input.LA(1) != '\n' && _input.LA(1) != '\r') {
                consumeCurrentCharacter();
            }
            if (_input.LA(1) == IntStream.EOF) {
                return emitEOF();
            }

            int startIndex = getCharIndex();
            int startLine = getLine();
            int startPosition = getCharPositionInLine();
            int current = _input.LA(1);
            if (current == '\n' || current == '\r') {
                String text = String.valueOf((char) consumeCurrentCharacter());
                if (current == '\r' && _input.LA(1) == '\n') {
                    text += (char) consumeCurrentCharacter();
                }
                return emitToken(NEWLINE, text, startIndex, startLine, startPosition);
            }
            if (Character.isLetterOrDigit(current) || current == '.' || current == '-') {
                String text = consumeWhile(value -> Character.isLetterOrDigit(value) || value == '.' || value == '-');
                return emitToken(WORD, text, startIndex, startLine, startPosition);
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
            if (current == '\n' || current == '\r') {
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
}
