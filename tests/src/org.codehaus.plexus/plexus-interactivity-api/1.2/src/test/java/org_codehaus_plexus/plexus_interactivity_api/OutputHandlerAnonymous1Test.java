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

import static org.assertj.core.api.Assertions.assertThat;

public class OutputHandlerAnonymous1Test {
    @Test
    void invokesOutputHandlerContract() throws IOException {
        StringBuilder output = new StringBuilder();
        OutputHandler outputHandler = new OutputHandler() {
            @Override
            public void write(String line) {
                output.append(line);
            }

            @Override
            public void writeLine(String line) {
                output.append(line).append(System.lineSeparator());
            }
        };

        outputHandler.write("hello");
        outputHandler.writeLine(" world");

        assertThat(output.toString()).isEqualTo("hello world" + System.lineSeparator());
    }
}
