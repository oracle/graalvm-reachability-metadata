/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tunnelvisionlabs.antlr4_runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.TestRig;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestRigTest {
    private static final int WORD = 1;

    @TempDir
    Path tempDir;

    @Test
    void loadsGeneratedLexerAndParserAndInvokesStartRule() throws Exception {
        TestRigGrammarParser.invocations.set(0);
        Path input = writeInputFile("alpha");
        TestRig testRig = new TestRig(new String[] {
                TestRigGrammar.class.getName(),
                "sentence",
                input.toString()
        });

        try {
            testRig.process();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        }

        assertThat(TestRigGrammarParser.invocations).hasValue(1);
    }

    @Test
    void fallsBackToPureLexerGrammarNameWhenLexerSuffixIsAbsent() throws Exception {
        PureLexerGrammar.tokensEmitted.set(0);
        Path input = writeInputFile("b");
        TestRig testRig = new TestRig(new String[] {
                PureLexerGrammar.class.getName(),
                TestRig.LEXER_START_RULE_NAME,
                input.toString()
        });

        try {
            testRig.process();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        }

        assertThat(PureLexerGrammar.tokensEmitted).hasValue(1);
    }

    private Path writeInputFile(String text) throws IOException {
        Path input = tempDir.resolve("input.txt");
        Files.write(input, text.getBytes(StandardCharsets.UTF_8));
        return input;
    }

    public static class TestRigGrammar {
    }

    public static class TestRigGrammarLexer extends Lexer {
        public TestRigGrammarLexer(CharStream input) {
            super(null);
            setInputStream(input);
        }

        @Override
        public void setInputStream(CharStream input) {
            _input = input;
            _hitEOF = false;
        }

        @Override
        public Token nextToken() {
            return nextTokenFromInput(this, false);
        }

        @Override
        public String[] getTokenNames() {
            return new String[] {"<INVALID>", "WORD"};
        }

        @Override
        public String[] getRuleNames() {
            return new String[] {"WORD"};
        }

        @Override
        public String getGrammarFileName() {
            return "TestRigGrammar";
        }
    }

    public static class TestRigGrammarParser extends Parser {
        static final AtomicInteger invocations = new AtomicInteger();

        public TestRigGrammarParser(TokenStream input) {
            super(input);
        }

        public ParserRuleContext sentence() {
            invocations.incrementAndGet();
            return new ParserRuleContext();
        }

        @Override
        public String[] getTokenNames() {
            return new String[] {"<INVALID>", "WORD"};
        }

        @Override
        public String[] getRuleNames() {
            return new String[] {"sentence"};
        }

        @Override
        public String getGrammarFileName() {
            return "TestRigGrammar";
        }
    }

    public static class PureLexerGrammar extends TestRigGrammarLexer {
        static final AtomicInteger tokensEmitted = new AtomicInteger();

        public PureLexerGrammar(CharStream input) {
            super(input);
        }

        @Override
        public Token nextToken() {
            return nextTokenFromInput(this, true);
        }

        @Override
        public String getGrammarFileName() {
            return "PureLexerGrammar";
        }
    }

    private static Token nextTokenFromInput(Lexer lexer, boolean countTokens) {
        CharStream input = lexer.getInputStream();
        if (input.LA(1) == IntStream.EOF) {
            return new CommonToken(Token.EOF, "<EOF>");
        }

        String text = Character.toString((char) input.LA(1));
        input.consume();
        if (countTokens) {
            PureLexerGrammar.tokensEmitted.incrementAndGet();
        }
        return new CommonToken(WORD, text);
    }
}
