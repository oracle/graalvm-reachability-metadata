/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_shell.spring_shell_core;

import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.core.command.Command;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.core.command.annotation.EnableCommand;

import static org.assertj.core.api.Assertions.assertThat;

public class EnableCommandRegistrarTest {

    @Test
    void registersAnnotatedCommandMethods() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ShellConfiguration.class);
            context.refresh();

            CommandRegistry commandRegistry = context.getBean(CommandRegistry.class);
            Command command = commandRegistry.getCommandByName("greeting");

            assertThat(command).isNotNull();
            assertThat(command.getDescription()).isEqualTo("Print a greeting");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCommand(SampleCommands.class)
    static class ShellConfiguration {

        @Bean
        SampleCommands sampleCommands() {
            return new SampleCommands();
        }
    }

    static class SampleCommands {

        @org.springframework.shell.core.command.annotation.Command(
                name = "greeting", description = "Print a greeting")
        String greeting() {
            return "Hello";
        }
    }
}
