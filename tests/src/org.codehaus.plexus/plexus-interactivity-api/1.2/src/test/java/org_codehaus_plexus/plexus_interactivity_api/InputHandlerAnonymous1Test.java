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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class InputHandlerAnonymous1Test {
    @Test
    void invokesInputHandlerContract() throws IOException {
        InputHandler inputHandler = new InputHandler() {
            @Override
            public String readLine() {
                return "line";
            }

            @Override
            public String readPassword() {
                return "password";
            }

            @Override
            public List<String> readMultipleLines() {
                return List.of("first", "second");
            }
        };

        assertThat(inputHandler.readLine()).isEqualTo("line");
        assertThat(inputHandler.readPassword()).isEqualTo("password");
        assertThat(inputHandler.readMultipleLines()).containsExactly("first", "second");
    }
}
