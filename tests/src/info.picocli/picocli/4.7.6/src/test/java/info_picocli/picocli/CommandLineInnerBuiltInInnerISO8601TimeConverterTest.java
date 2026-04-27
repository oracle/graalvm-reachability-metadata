/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import java.sql.Time;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Option;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineInnerBuiltInInnerISO8601TimeConverterTest {

    @Test
    void convertsIso8601TimeOptionsToSqlTime() {
        SqlTimeCommand command = new SqlTimeCommand();

        new CommandLine(command).parseArgs(
                "--hour-minute", "08:15",
                "--hour-minute-second", "09:16:30",
                "--millis-dot", "10:17:31.123",
                "--millis-comma", "11:18:32,456");

        assertThat(command.hourMinute.toString()).isEqualTo("08:15:00");
        assertThat(command.hourMinuteSecond.toString()).isEqualTo("09:16:30");
        assertThat(command.millisDot.toString()).isEqualTo("10:17:31");
        assertThat(command.millisComma.toString()).isEqualTo("11:18:32");
    }

    public static class SqlTimeCommand {
        @Option(names = "--hour-minute", required = true)
        private Time hourMinute;

        @Option(names = "--hour-minute-second", required = true)
        private Time hourMinuteSecond;

        @Option(names = "--millis-dot", required = true)
        private Time millisDot;

        @Option(names = "--millis-comma", required = true)
        private Time millisComma;
    }
}
