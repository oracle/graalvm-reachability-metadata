/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckDocLinksTaskTests {

    @TempDir
    Path tempDir;

    @Test
    void resolvesInlineAndReferenceLinksToExistingPaths() throws IOException {
        Files.writeString(tempDir.resolve("target.md"), "# Target\n");
        Files.writeString(tempDir.resolve("README.md"), """
                See [the target](target.md#target), [the site](https://example.com) and [ref].

                [ref]: target.md
                """);

        assertThatCode(createTask()::check).doesNotThrowAnyException();
    }

    @Test
    void reportsBrokenLinkAfterLongTextOpenedByEscapedBracket() throws IOException {
        // A GraalVM ticket snapshot escapes the brackets in its summary, so no `]` closes the text
        // that `\[` opens; a recursive match of that text used to overflow the stack.
        String body = "- Status: Open\n".repeat(50_000);
        Files.writeString(tempDir.resolve("tickets.md"),
                "## GR-1: \\[Native Image\\] summary\n\n" + body + "\nSee [the missing doc](missing.md).\n");

        assertThatThrownBy(createTask()::check)
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("tickets.md:")
                .hasMessageContaining("target not found: missing.md")
                .hasMessageContaining("Total broken links: 1");
    }

    private CheckDocLinksTask createTask() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        return project.getTasks()
                .register("checkDocLinks", CheckDocLinksTask.class, task -> task.getRepoRoot().set(tempDir.toFile()))
                .get();
    }
}
