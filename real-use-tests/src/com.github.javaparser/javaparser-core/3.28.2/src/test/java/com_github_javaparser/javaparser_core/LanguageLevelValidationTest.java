/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.RecordDeclaration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JavaParser's language-level validation subsystem for a source-analysis service.
public class LanguageLevelValidationTest {
    @Test
    public void validatesSourceAgainstTheConfiguredJavaLanguageLevel() {
        String source = "public record Customer(String id) {}";

        ParseResult<CompilationUnit> modernResult = parseAt(ParserConfiguration.LanguageLevel.JAVA_16, source);
        RecordDeclaration customer = modernResult.getResult()
                .orElseThrow()
                .findFirst(RecordDeclaration.class)
                .orElseThrow();

        ParseResult<CompilationUnit> legacyResult = parseAt(ParserConfiguration.LanguageLevel.JAVA_8, source);

        assertTrue(modernResult.isSuccessful());
        assertEquals("Customer", customer.getNameAsString());
        assertEquals("String", customer.getParameters().get(0).getType().asString());
        assertFalse(legacyResult.isSuccessful());
        assertTrue(legacyResult.getProblems().stream()
                .anyMatch(problem -> problem.getMessage().contains("Record Declarations are not supported")));
    }

    private ParseResult<CompilationUnit> parseAt(
            ParserConfiguration.LanguageLevel languageLevel, String source) {
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(languageLevel);
        return new JavaParser(configuration).parse(source);
    }
}
