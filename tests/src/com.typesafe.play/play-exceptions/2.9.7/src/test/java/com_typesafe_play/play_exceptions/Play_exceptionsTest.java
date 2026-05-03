/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_exceptions;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import play.api.PlayException;
import play.api.UsefulException;

import static org.assertj.core.api.Assertions.assertThat;

public class Play_exceptionsTest {
    @Test
    void playExceptionPreservesDiagnosticFieldsAndMessageWithoutCause() {
        PlayException exception = new PlayException("Configuration error", "Missing application secret");

        assertThat(exception.title).isEqualTo("Configuration error");
        assertThat(exception.description).isEqualTo("Missing application secret");
        assertThat(exception.cause).isNull();
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getMessage()).isEqualTo("Configuration error[Missing application secret]");
        assertThat(exception.id).isNotBlank();
        assertThat(exception.id).matches("[0-9a-p]+");
        assertThat(exception.toString())
                .isEqualTo("@" + exception.id + ": Configuration error[Missing application secret]");
    }

    @Test
    void playExceptionPreservesBothPublicAndRuntimeCauses() {
        IllegalStateException cause = new IllegalStateException("boom");

        PlayException exception = new PlayException("Runtime error", "Handler failed", cause);

        assertThat(exception.title).isEqualTo("Runtime error");
        assertThat(exception.description).isEqualTo("Handler failed");
        assertThat(exception.cause).isSameAs(cause);
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getMessage()).isEqualTo("Runtime error[Handler failed]");
        assertThat(exception.toString()).isEqualTo("@" + exception.id + ": Runtime error[Handler failed]");
    }

    @Test
    void usefulExceptionAllowsCustomMessagesAndRuntimeCauses() {
        IllegalArgumentException cause = new IllegalArgumentException("invalid state");

        CustomUsefulException exception = new CustomUsefulException("Custom diagnostic", cause, "custom-id");

        assertThat(exception.getMessage()).isEqualTo("Custom diagnostic");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.id).isEqualTo("custom-id");
        assertThat(exception.title).isNull();
        assertThat(exception.description).isNull();
        assertThat(exception.cause).isNull();
        assertThat(exception.toString()).isEqualTo("@custom-id: Custom diagnostic");
    }

    @Test
    void usefulExceptionAllowsMessageOnlyCustomExceptions() {
        CustomUsefulException exception = new CustomUsefulException("Custom diagnostic", "custom-id");

        assertThat(exception.getMessage()).isEqualTo("Custom diagnostic");
        assertThat(exception.getCause()).isNull();
        assertThat(exception.id).isEqualTo("custom-id");
        assertThat(exception.toString()).isEqualTo("@custom-id: Custom diagnostic");
    }

    @Test
    void exceptionSourceFormatsLocationAndReturnsImplementedAccessors() {
        IllegalStateException cause = new IllegalStateException("bad token");

        SourceException exception = new SourceException(
                "Template compilation failed",
                "Unexpected token",
                cause,
                "views/home.scala.html",
                12,
                7,
                "@main {\n  <h1>Hello</h1>\n}");

        assertThat(exception.sourceName()).isEqualTo("views/home.scala.html");
        assertThat(exception.line()).isEqualTo(12);
        assertThat(exception.position()).isEqualTo(7);
        assertThat(exception.input()).isEqualTo("@main {\n  <h1>Hello</h1>\n}");
        assertThat(exception.cause).isSameAs(cause);
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.toString())
                .isEqualTo("@" + exception.id
                        + ": Template compilation failed[Unexpected token] in views/home.scala.html:12");
    }

    @Test
    void interestingLinesReturnsCenteredWindowUsingUnixAndWindowsLineSeparators() {
        SourceException exception = new SourceException(
                "Source error",
                "Bad middle line",
                "script.conf",
                3,
                4,
                "alpha\nbeta\r\ngamma\ndelta\nepsilon");

        PlayException.InterestingLines interestingLines = exception.interestingLines(1);

        assertThat(interestingLines.firstLine).isEqualTo(2);
        assertThat(interestingLines.errorLine).isEqualTo(1);
        assertThat(interestingLines.focus).containsExactly("beta", "gamma", "delta");
    }

    @Test
    void interestingLinesClampsWindowAtBeginningAndEndOfInput() {
        SourceException firstLineException = new SourceException(
                "Source error",
                "Bad first line",
                "script.conf",
                1,
                1,
                "alpha\nbeta\ngamma");
        SourceException lastLineException = new SourceException(
                "Source error",
                "Bad last line",
                "script.conf",
                3,
                1,
                "alpha\nbeta\ngamma");

        PlayException.InterestingLines firstLineWindow = firstLineException.interestingLines(3);
        PlayException.InterestingLines lastLineWindow = lastLineException.interestingLines(3);

        assertThat(firstLineWindow.firstLine).isEqualTo(1);
        assertThat(firstLineWindow.errorLine).isEqualTo(0);
        assertThat(firstLineWindow.focus).containsExactly("alpha", "beta", "gamma");
        assertThat(lastLineWindow.firstLine).isEqualTo(1);
        assertThat(lastLineWindow.errorLine).isEqualTo(2);
        assertThat(lastLineWindow.focus).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void interestingLinesReturnsSingleErrorLineWhenContextIsZero() {
        SourceException exception = new SourceException(
                "Source error",
                "Only the failing line is requested",
                "script.conf",
                2,
                10,
                "alpha\nbeta\ngamma");

        PlayException.InterestingLines interestingLines = exception.interestingLines(0);

        assertThat(interestingLines.firstLine).isEqualTo(2);
        assertThat(interestingLines.errorLine).isEqualTo(0);
        assertThat(interestingLines.focus).containsExactly("beta");
    }

    @Test
    void interestingLinesPreservesBlankLinesInSourceContext() {
        SourceException exception = new SourceException(
                "Source error",
                "Blank line failed",
                "script.conf",
                2,
                1,
                "alpha\n\ngamma\ndelta");

        PlayException.InterestingLines interestingLines = exception.interestingLines(1);

        assertThat(interestingLines.firstLine).isEqualTo(1);
        assertThat(interestingLines.errorLine).isEqualTo(1);
        assertThat(interestingLines.focus).containsExactly("alpha", "", "gamma");
    }

    @Test
    void interestingLinesDropsTrailingEmptyInputLinesLikePatternSplit() {
        SourceException exception = new SourceException(
                "Source error",
                "Trailing blank input",
                "script.conf",
                2,
                1,
                "alpha\nbeta\n");

        PlayException.InterestingLines interestingLines = exception.interestingLines(2);

        assertThat(interestingLines.firstLine).isEqualTo(1);
        assertThat(interestingLines.errorLine).isEqualTo(1);
        assertThat(interestingLines.focus).containsExactly("alpha", "beta");
    }

    @Test
    void interestingLinesDoesNotRequireColumnPosition() {
        SourceException exception = new SourceException(
                "Source error",
                "No column position is available",
                "routes",
                2,
                null,
                "GET     /           controllers.Home.index\n"
                        + "POST    /items      controllers.Items.create\n"
                        + "DELETE  /items/:id  controllers.Items.delete");

        PlayException.InterestingLines interestingLines = exception.interestingLines(1);

        assertThat(exception.position()).isNull();
        assertThat(interestingLines.firstLine).isEqualTo(1);
        assertThat(interestingLines.errorLine).isEqualTo(1);
        assertThat(interestingLines.focus).containsExactly(
                "GET     /           controllers.Home.index",
                "POST    /items      controllers.Items.create",
                "DELETE  /items/:id  controllers.Items.delete");
    }

    @Test
    void interestingLinesIsUnavailableWhenInputOrLineIsMissing() {
        SourceException withoutInput = new SourceException(
                "Source error",
                "No source body",
                "generated.conf",
                4,
                1,
                null);
        SourceException withoutLine = new SourceException(
                "Source error",
                "No line number",
                "generated.conf",
                null,
                1,
                "alpha\nbeta");

        assertThat(withoutInput.interestingLines(2)).isNull();
        assertThat(withoutLine.interestingLines(2)).isNull();
    }

    @Test
    void interestingLinesReturnsNullWhenSourceContentCannotBeRead() {
        UnreadableSourceException exception = new UnreadableSourceException(
                "Source error",
                "Source content failed to load");
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();

        try (PrintStream replacementErr = new PrintStream(capturedErr, true, StandardCharsets.UTF_8)) {
            System.setErr(replacementErr);

            assertThat(exception.interestingLines(2)).isNull();
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void interestingLinesCanBeConstructedDirectlyForPrecomputedFocus() {
        String[] focus = {"line 5", "line 6", "line 7"};

        PlayException.InterestingLines interestingLines = new PlayException.InterestingLines(5, focus, 1);

        assertThat(interestingLines.firstLine).isEqualTo(5);
        assertThat(interestingLines.errorLine).isEqualTo(1);
        assertThat(interestingLines.focus).isSameAs(focus);
    }

    @Test
    void exceptionAttachmentExposesSubtitleAndContentWithCause() {
        IllegalArgumentException cause = new IllegalArgumentException("invalid snippet");

        AttachmentException exception = new AttachmentException(
                "Bad request",
                "Validation failed",
                cause,
                "Submitted payload",
                "name must not be blank");

        assertThat(exception.title).isEqualTo("Bad request");
        assertThat(exception.description).isEqualTo("Validation failed");
        assertThat(exception.cause).isSameAs(cause);
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.subTitle()).isEqualTo("Submitted payload");
        assertThat(exception.content()).isEqualTo("name must not be blank");
        assertThat(exception.toString()).isEqualTo("@" + exception.id + ": Bad request[Validation failed]");
    }

    @Test
    void exceptionAttachmentSupportsMessageOnlyConstructor() {
        AttachmentException exception = new AttachmentException(
                "Bad request",
                "Validation failed",
                "Submitted payload",
                "name must not be blank");

        assertThat(exception.getCause()).isNull();
        assertThat(exception.cause).isNull();
        assertThat(exception.subTitle()).isEqualTo("Submitted payload");
        assertThat(exception.content()).isEqualTo("name must not be blank");
        assertThat(exception.getMessage()).isEqualTo("Bad request[Validation failed]");
    }

    @Test
    void richDescriptionAddsHtmlDescriptionToAttachmentContract() {
        RichAttachmentException exception = new RichAttachmentException(
                "Template error",
                "Compilation failed",
                "Generated source",
                "plain source excerpt",
                "<pre>plain source excerpt</pre>");

        assertThat(exception.subTitle()).isEqualTo("Generated source");
        assertThat(exception.content()).isEqualTo("plain source excerpt");
        assertThat(exception.htmlDescription()).isEqualTo("<pre>plain source excerpt</pre>");
        assertThat(exception.getMessage()).isEqualTo("Template error[Compilation failed]");
        assertThat(exception.getCause()).isNull();
        assertThat(exception.cause).isNull();
    }

    @Test
    void richDescriptionPreservesCauseWhenProvided() {
        IllegalStateException cause = new IllegalStateException("template failed");

        RichAttachmentException exception = new RichAttachmentException(
                "Template error",
                "Compilation failed",
                cause,
                "Generated source",
                "plain source excerpt",
                "<pre>plain source excerpt</pre>");

        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.cause).isSameAs(cause);
        assertThat(exception.subTitle()).isEqualTo("Generated source");
        assertThat(exception.content()).isEqualTo("plain source excerpt");
        assertThat(exception.htmlDescription()).isEqualTo("<pre>plain source excerpt</pre>");
    }

    private static final class CustomUsefulException extends UsefulException {
        private CustomUsefulException(String message, String id) {
            super(message);
            this.id = id;
        }

        private CustomUsefulException(String message, Throwable cause, String id) {
            super(message, cause);
            this.id = id;
        }
    }

    private static final class SourceException extends PlayException.ExceptionSource {
        private final String sourceName;
        private final Integer line;
        private final Integer position;
        private final String input;

        private SourceException(
                String title,
                String description,
                String sourceName,
                Integer line,
                Integer position,
                String input) {
            super(title, description);
            this.sourceName = sourceName;
            this.line = line;
            this.position = position;
            this.input = input;
        }

        private SourceException(
                String title,
                String description,
                Throwable cause,
                String sourceName,
                Integer line,
                Integer position,
                String input) {
            super(title, description, cause);
            this.sourceName = sourceName;
            this.line = line;
            this.position = position;
            this.input = input;
        }

        @Override
        public Integer line() {
            return line;
        }

        @Override
        public Integer position() {
            return position;
        }

        @Override
        public String input() {
            return input;
        }

        @Override
        public String sourceName() {
            return sourceName;
        }
    }

    private static final class UnreadableSourceException extends PlayException.ExceptionSource {
        private UnreadableSourceException(String title, String description) {
            super(title, description);
        }

        @Override
        public Integer line() {
            return 1;
        }

        @Override
        public Integer position() {
            return 1;
        }

        @Override
        public String input() {
            throw new IllegalStateException("source content unavailable");
        }

        @Override
        public String sourceName() {
            return "unreadable.conf";
        }
    }

    private static final class AttachmentException extends PlayException.ExceptionAttachment {
        private final String subTitle;
        private final String content;

        private AttachmentException(
                String title,
                String description,
                Throwable cause,
                String subTitle,
                String content) {
            super(title, description, cause);
            this.subTitle = subTitle;
            this.content = content;
        }

        private AttachmentException(String title, String description, String subTitle, String content) {
            super(title, description);
            this.subTitle = subTitle;
            this.content = content;
        }

        @Override
        public String subTitle() {
            return subTitle;
        }

        @Override
        public String content() {
            return content;
        }
    }

    private static final class RichAttachmentException extends PlayException.RichDescription {
        private final String subTitle;
        private final String content;
        private final String htmlDescription;

        private RichAttachmentException(
                String title,
                String description,
                String subTitle,
                String content,
                String htmlDescription) {
            super(title, description);
            this.subTitle = subTitle;
            this.content = content;
            this.htmlDescription = htmlDescription;
        }

        private RichAttachmentException(
                String title,
                String description,
                Throwable cause,
                String subTitle,
                String content,
                String htmlDescription) {
            super(title, description, cause);
            this.subTitle = subTitle;
            this.content = content;
            this.htmlDescription = htmlDescription;
        }

        @Override
        public String subTitle() {
            return subTitle;
        }

        @Override
        public String content() {
            return content;
        }

        @Override
        public String htmlDescription() {
            return htmlDescription;
        }
    }
}
