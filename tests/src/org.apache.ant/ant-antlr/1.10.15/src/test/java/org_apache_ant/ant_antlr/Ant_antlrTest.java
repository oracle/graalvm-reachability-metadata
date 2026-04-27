/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant_antlr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.optional.ANTLR;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Ant_antlrTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesJavaSourcesForAParserAndLexerGrammar() throws Exception {
        Path grammar = writeFile("grammars/SimpleCalc.g", """
            class SimpleCalcParser extends Parser;
            expr
                : INT (PLUS INT)* EOF
                ;

            class SimpleCalcLexer extends Lexer;
            PLUS: '+';
            INT: ('0'..'9')+;
            WS: (' ' | '\\t' | '\\n' | '\\r')+ { $setType(Token.SKIP); };
            """);
        Path outputDirectory = Files.createDirectory(temporaryDirectory.resolve("generated-java"));

        ANTLR task = newAntlrTask();
        task.setTarget(grammar.toFile());
        task.setOutputdirectory(outputDirectory.toFile());
        addRuntimeClasspath(task);

        task.execute();

        Path parser = outputDirectory.resolve("SimpleCalcParser.java");
        Path lexer = outputDirectory.resolve("SimpleCalcLexer.java");
        Path tokenTypes = outputDirectory.resolve("SimpleCalcParserTokenTypes.java");
        assertThat(parser).exists();
        assertThat(lexer).exists();
        assertThat(tokenTypes).exists();
        assertThat(Files.readString(parser)).contains("public class SimpleCalcParser extends antlr.LLkParser");
        assertThat(Files.readString(lexer)).contains("public class SimpleCalcLexer extends antlr.CharScanner");
    }

    @Test
    void generatesHtmlDocumentationWhenHtmlModeIsEnabled() throws Exception {
        Path grammar = writeFile("html/HtmlCalc.g", """
            class HtmlCalcParser extends Parser;
            expr
                : NUMBER EOF
                ;

            class HtmlCalcLexer extends Lexer;
            NUMBER: ('0'..'9')+;
            WS: (' ' | '\\t' | '\\n' | '\\r')+ { $setType(Token.SKIP); };
            """);
        Path outputDirectory = Files.createDirectory(temporaryDirectory.resolve("generated-html"));

        ANTLR task = newAntlrTask();
        task.setTarget(grammar.toFile());
        task.setOutputdirectory(outputDirectory.toFile());
        task.setHtml(true);
        addRuntimeClasspath(task);

        task.execute();

        Path parserDocumentation = outputDirectory.resolve("HtmlCalcParser.html");
        Path lexerDocumentation = outputDirectory.resolve("HtmlCalcLexer.html");
        assertThat(parserDocumentation).exists();
        assertThat(lexerDocumentation).exists();
        assertThat(Files.readString(parserDocumentation)).contains("HtmlCalcParser");
        assertThat(Files.readString(lexerDocumentation)).contains("HtmlCalcLexer");
    }

    @Test
    void skipsExecutionWhenGeneratedFileIsNewerThanGrammar() throws Exception {
        Path grammar = writeFile("skip/ExistingParser.g", """
            class ExistingParser extends Parser;
            start
                : EOF
                ;
            """);
        Path outputDirectory = Files.createDirectory(temporaryDirectory.resolve("existing-output"));
        Path generatedParser = outputDirectory.resolve("ExistingParser.java");
        Files.writeString(generatedParser, "// already generated", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(generatedParser, FileTime.from(Instant.now().plusSeconds(60)));

        ANTLR task = newAntlrTask();
        task.setTarget(grammar.toFile());
        task.setOutputdirectory(outputDirectory.toFile());

        task.execute();

        assertThat(Files.readString(generatedParser)).isEqualTo("// already generated");
    }

    @Test
    void rejectsMissingTargetFile() {
        ANTLR task = newAntlrTask();
        task.setTarget(temporaryDirectory.resolve("missing.g").toFile());

        assertThatThrownBy(task::execute)
            .isInstanceOf(BuildException.class)
            .hasMessageContaining("Invalid target");
    }

    @Test
    void rejectsOutputDirectoryThatIsNotADirectory() throws IOException {
        Path grammar = writeFile("invalid-output/BrokenOutputParser.g", """
            class BrokenOutputParser extends Parser;
            start
                : EOF
                ;
            """);
        Path notDirectory = writeFile("invalid-output/not-a-directory", "plain file");

        ANTLR task = newAntlrTask();
        task.setTarget(grammar.toFile());
        task.setOutputdirectory(notDirectory.toFile());

        assertThatThrownBy(task::execute)
            .isInstanceOf(BuildException.class)
            .hasMessageContaining("Invalid output directory");
    }

    @Test
    void reportsGrammarFilesWithoutAGeneratedClassDeclaration() throws IOException {
        Path grammar = writeFile("invalid-grammar/MissingClass.g", "start : EOF ;");

        ANTLR task = newAntlrTask();
        task.setTarget(grammar.toFile());

        assertThatThrownBy(task::execute)
            .isInstanceOf(BuildException.class)
            .hasMessageContaining("Unable to determine generated class");
    }

    private ANTLR newAntlrTask() {
        Project project = new Project();
        project.setBaseDir(temporaryDirectory.toFile());
        project.init();

        ANTLR task = new ANTLR();
        task.setProject(project);
        task.setTaskName("antlr");
        task.init();
        return task;
    }

    private void addRuntimeClasspath(ANTLR task) {
        String classpath = System.getProperty("java.class.path", "");
        for (String entry : classpath.split(Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                task.createClasspath().setLocation(Path.of(entry).toFile());
            }
        }
    }

    private Path writeFile(String relativePath, String content) throws IOException {
        Path file = temporaryDirectory.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
