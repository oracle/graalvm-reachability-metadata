/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_shell.spring_shell_core;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.core.InputReader;
import org.springframework.shell.core.command.Command;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.core.command.ExitStatus;
import org.springframework.shell.core.command.ParsedInput;
import org.springframework.shell.core.command.annotation.EnableCommand;

import static org.assertj.core.api.Assertions.assertThat;

public class EnableCommandRegistrarTest {

    @Test
    void enableCommandRegistersAnnotatedMethodsFromCandidateClasses() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                CommandConfiguration.class)) {
            CommandRegistry commandRegistry = context.getBean(CommandRegistry.class);

            Command command = commandRegistry.getCommandByName("sample greet");

            assertThat(command).isNotNull();
            assertThat(command.getDescription()).isEqualTo("Returns a greeting");
            StringWriter output = new StringWriter();
            ExitStatus exitStatus = command.execute(new CommandContext(
                    ParsedInput.builder().commandName("sample").addSubCommand("greet").build(),
                    commandRegistry,
                    new PrintWriter(output),
                    new NoOpInputReader()));
            assertThat(exitStatus).isEqualTo(ExitStatus.OK);
            assertThat(output.toString()).contains("Hello from Spring Shell");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCommand(SampleCommands.class)
    public static class CommandConfiguration {

        @Bean
        public SampleCommands sampleCommands() {
            return new SampleCommands();
        }

    }

    public static class SampleCommands {

        @org.springframework.shell.core.command.annotation.Command(
                name = "sample greet",
                description = "Returns a greeting")
        public String greet() {
            return "Hello from Spring Shell";
        }

        String helperMethod() {
            return "not a command";
        }

    }

    static class NoOpInputReader implements InputReader {
    }

}
