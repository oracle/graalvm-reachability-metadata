/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_interactivity_api;

import org.codehaus.plexus.components.interactivity.OutputHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OutputHandlerAnonymous1Test {
    @Test
    void writesTextAndLines() throws IOException {
        List<String> output = new ArrayList<>();
        OutputHandler outputHandler = new OutputHandler() {
            @Override
            public void write(String line) {
                output.add(line);
            }

            @Override
            public void writeLine(String line) {
                output.add(line + System.lineSeparator());
            }
        };

        outputHandler.write("prompt");
        outputHandler.writeLine("answer");

        assertThat(output).containsExactly("prompt", "answer" + System.lineSeparator());
    }
}
