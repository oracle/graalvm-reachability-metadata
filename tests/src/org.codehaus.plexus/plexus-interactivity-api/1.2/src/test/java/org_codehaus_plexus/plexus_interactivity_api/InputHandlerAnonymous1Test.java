/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_interactivity_api;

import org.codehaus.plexus.components.interactivity.InputHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class InputHandlerAnonymous1Test {
    @Test
    void readsSinglePasswordAndMultipleLines() throws IOException {
        Deque<String> input = new ArrayDeque<>(List.of("first", "secret", "alpha", "beta", ""));
        InputHandler inputHandler = new InputHandler() {
            @Override
            public String readLine() {
                return input.removeFirst();
            }

            @Override
            public String readPassword() {
                return input.removeFirst();
            }

            @Override
            public List<String> readMultipleLines() {
                List<String> lines = new ArrayList<>();
                String line = input.removeFirst();
                while (!line.isEmpty()) {
                    lines.add(line);
                    line = input.removeFirst();
                }
                return lines;
            }
        };

        assertThat(inputHandler.readLine()).isEqualTo("first");
        assertThat(inputHandler.readPassword()).isEqualTo("secret");
        assertThat(inputHandler.readMultipleLines()).containsExactly("alpha", "beta");
    }
}
