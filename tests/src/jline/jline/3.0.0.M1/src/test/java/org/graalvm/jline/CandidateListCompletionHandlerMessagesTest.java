/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import jline.UnsupportedTerminal;
import jline.console.ConsoleReader;
import jline.console.completer.CandidateListCompletionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class CandidateListCompletionHandlerMessagesTest {

    private Locale originalLocale;

    @BeforeEach
    void setUp() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void printCandidatesLoadsTheMessagesBundleWhenPromptingForConfirmation() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (ConsoleReader reader = new ConsoleReader(
                "candidate-list-completion-handler",
                new ByteArrayInputStream("y".getBytes(StandardCharsets.UTF_8)),
                output,
                new UnsupportedTerminal())) {
            reader.setAutoprintThreshold(1);

            CandidateListCompletionHandler.printCandidates(reader, Arrays.<CharSequence>asList("alpha", "beta"));
            reader.flush();
        }

        String consoleOutput = output.toString(StandardCharsets.UTF_8.name());
        assertThat(consoleOutput)
                .contains("Display all 2 possibilities? (y or n)")
                .contains("alpha")
                .contains("beta");
    }
}
