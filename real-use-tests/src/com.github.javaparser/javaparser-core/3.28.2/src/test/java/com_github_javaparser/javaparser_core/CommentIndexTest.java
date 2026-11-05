/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.comments.Comment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JavaParser's comment model subsystem to index actionable notes in a source file.
public class CommentIndexTest {
    @Test
    public void indexesTodoCommentsWithTheirSourceLocations() {
        String source = """
                package app;

                /* Export records after validation. */
                public class Exporter {
                    // TODO: send the validated records to the warehouse.
                    public void export() {
                    }
                }
                """;

        CompilationUnit compilationUnit = StaticJavaParser.parse(source);
        List<SourceNote> notes = compilationUnit.getAllContainedComments().stream()
                .filter(comment -> comment.getContent().contains("TODO"))
                .map(this::sourceNote)
                .collect(Collectors.toList());

        assertEquals(List.of(new SourceNote("TODO: send the validated records to the warehouse.", 5)), notes);
        assertTrue(compilationUnit.getAllContainedComments().stream()
                .anyMatch(Comment::isBlockComment));
    }

    private SourceNote sourceNote(Comment comment) {
        return new SourceNote(comment.getContent().trim(), comment.getRange().orElseThrow().begin.line);
    }

    private record SourceNote(String text, int line) {
    }
}
