/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import com.github.javaparser.JavaToken;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JavaParser's token-stream subsystem to classify source for an editor outline.
public class TokenStreamAnalysisTest {
    @Test
    public void classifiesMethodTokensForAnEditor() {
        String source = """
                package billing;

                public class InvoiceService {
                    public String issue(String invoiceId) {
                        return invoiceId + "-issued";
                    }
                }
                """;

        CompilationUnit compilationUnit = StaticJavaParser.parse(source);
        MethodDeclaration issue = compilationUnit.findFirst(
                MethodDeclaration.class, method -> method.getNameAsString().equals("issue"))
                .orElseThrow();
        TokenRange tokenRange = issue.getTokenRange().orElseThrow();
        List<Token> syntaxTokens = tokensForEditor(tokenRange);

        assertEquals(new Token("public", JavaToken.Category.KEYWORD), syntaxTokens.get(0));
        assertEquals(new Token("issue", JavaToken.Category.IDENTIFIER), syntaxTokens.get(2));
        assertEquals(new Token("+", JavaToken.Category.OPERATOR), syntaxTokens.get(10));
        assertEquals(new Token("\"-issued\"", JavaToken.Category.LITERAL), syntaxTokens.get(11));
        assertEquals(new Token("}", JavaToken.Category.SEPARATOR), syntaxTokens.get(13));
        assertEquals(14, syntaxTokens.size());
        assertEquals(4, tokenRange.toRange().orElseThrow().begin.line);
        assertEquals(4, issue.getRange().orElseThrow().begin.line);
    }

    private List<Token> tokensForEditor(TokenRange tokenRange) {
        return StreamSupport.stream(tokenRange.spliterator(), false)
                .filter(token -> !token.getCategory().isWhitespace())
                .map(token -> new Token(token.getText(), token.getCategory()))
                .collect(Collectors.toList());
    }

    private record Token(String text, JavaToken.Category category) {
    }
}
