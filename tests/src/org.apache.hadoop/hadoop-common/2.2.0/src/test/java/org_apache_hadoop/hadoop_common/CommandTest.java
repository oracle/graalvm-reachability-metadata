/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.shell.Command;
import org.junit.jupiter.api.Test;

public class CommandTest {
    @Test
    void commandMetadataAccessorsReadSubclassFields() {
        MetadataCommand command = new MetadataCommand();

        assertThat(command.getName()).isEqualTo("metadata");
        assertThat(command.getUsage()).isEqualTo("-metadata <path>");
        assertThat(command.getDescription()).isEqualTo("Reads command metadata from static fields.");
    }

    public static class MetadataCommand extends Command {
        public static final String NAME = "metadata";
        public static final String USAGE = "<path>";
        public static final String DESCRIPTION = "Reads command metadata from static fields.";

        @Override
        public String getCommandName() {
            return NAME;
        }

        @Override
        protected void run(Path path) throws IOException {
        }
    }
}
