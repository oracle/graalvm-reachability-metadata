/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.CommaParameterSplitter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JCommander's comma-separated collection parameter subsystem for a data export CLI.
public class ExportColumnsCommandLineTest {
    @Test
    public void parsesACommaSeparatedColumnSelectionForAnExportJob() {
        ExportOptions options = new ExportOptions();
        JCommander commander = JCommander.newBuilder()
                .programName("data-export")
                .addObject(options)
                .build();

        commander.parse(
                "--format", "csv",
                "--columns", "account_id,display_name,created_at");

        assertEquals("csv", options.format);
        assertEquals(List.of("account_id", "display_name", "created_at"), options.columns);
    }

    @Parameters(commandDescription = "Export selected columns from the account database")
    public static class ExportOptions {
        @Parameter(names = "--format", description = "output format", required = true)
        private String format;

        @Parameter(
                names = "--columns",
                description = "comma-separated columns to export",
                required = true,
                splitter = CommaParameterSplitter.class)
        private List<String> columns = new ArrayList<>();
    }
}
