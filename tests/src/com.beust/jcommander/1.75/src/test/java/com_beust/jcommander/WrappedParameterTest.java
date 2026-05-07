/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.JCommander;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WrappedParameterTest {
    @Test
    void parsesDynamicParametersIntoMap() {
        DynamicParametersCommand command = new DynamicParametersCommand();

        new JCommander(command, "-D", "host=localhost", "-Dport=8080");

        assertThat(command.definitions)
                .containsEntry("host", "localhost")
                .containsEntry("port", "8080");
    }

    public static class DynamicParametersCommand {
        @DynamicParameter(names = "-D")
        private Map<String, String> definitions = new LinkedHashMap<>();
    }
}
