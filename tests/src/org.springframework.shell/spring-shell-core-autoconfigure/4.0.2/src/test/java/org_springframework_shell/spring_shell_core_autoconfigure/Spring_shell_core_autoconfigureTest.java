/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_shell.spring_shell_core_autoconfigure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jline.reader.Candidate;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.Command;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.CommandOption;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.core.command.DefaultCommandParser;
import org.springframework.shell.core.command.ExitStatus;
import org.springframework.shell.core.command.ParsedInput;
import org.springframework.shell.core.config.UserConfigPathProvider;
import org.springframework.shell.core.autoconfigure.CommandRegistryAutoConfiguration;
import org.springframework.shell.core.autoconfigure.JLineShellAutoConfiguration;
import org.springframework.shell.core.autoconfigure.ShellRunnerAutoConfiguration;
import org.springframework.shell.core.autoconfigure.SpringShellProperties;
import org.springframework.shell.core.autoconfigure.StandardCommandsAutoConfiguration;
import org.springframework.shell.core.autoconfigure.TerminalCustomizer;
import org.springframework.shell.core.autoconfigure.UserConfigAutoConfiguration;
import org.springframework.shell.jline.CommandCompleter;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.shell.jline.tui.component.ViewComponentExecutor;
import org.springframework.shell.jline.tui.component.flow.ComponentFlow;
import org.springframework.shell.jline.tui.component.view.TerminalUIBuilder;
import org.springframework.shell.jline.tui.style.TemplateExecutor;
import org.springframework.shell.jline.tui.style.ThemeActive;
import org.springframework.shell.jline.tui.style.ThemeRegistry;
import org.springframework.shell.jline.tui.style.ThemeResolver;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_shell_core_autoconfigureTest {

    @Test
    void commandRegistryAutoConfigurationRegistersProgrammaticAndAnnotatedCommands() throws Exception {
        try (AnnotationConfigApplicationContext context = newContext(Collections.emptyMap(),
                CommandRegistryAutoConfiguration.class, CommandConfiguration.class, GreetingCommands.class)) {
            CommandRegistry registry = context.getBean(CommandRegistry.class);

            Command programmatic = registry.getCommandByName("programmatic");
            assertThat(programmatic).isNotNull();
            assertThat(programmatic.getDescription()).isEqualTo("registered as a Command bean");

            Command annotated = registry.getCommandByName("greet");
            assertThat(annotated).isNotNull();
            assertThat(annotated.getAliases()).containsExactly("hello");
            assertThat(annotated.getOptions()).extracting(CommandOption::longName).containsExactly("name");

            StringWriter output = new StringWriter();
            CommandContext commandContext = new CommandContext(
                    ParsedInput.builder()
                        .commandName("greet")
                        .addOption(CommandOption.with()
                            .longName("name")
                            .value("native image")
                            .type(String.class)
                            .build())
                        .build(),
                    registry, new PrintWriter(output), null);

            ExitStatus exitStatus = annotated.execute(commandContext);

            assertThat(exitStatus).isEqualTo(ExitStatus.OK);
            assertThat(output.toString()).contains("Hello native image");
            assertThat(registry.getCommandByName("quit")).isNotNull();
        }
    }

    @Test
    void standardCommandsHonorConfigurationPropertiesAndBuildInformation() throws Exception {
        Map<String, Object> properties = Map.of(
                "spring.shell.command.clear.enabled", "false",
                "spring.shell.command.script.enabled", "false",
                "spring.shell.command.version.show-build-group", "true",
                "spring.shell.command.version.show-build-artifact", "true",
                "spring.shell.command.version.show-build-name", "true",
                "spring.shell.command.version.show-build-version", "true",
                "spring.shell.command.version.show-build-time", "false");

        try (AnnotationConfigApplicationContext context = newContext(properties,
                StandardCommandsAutoConfiguration.class, BuildInfoConfiguration.class)) {
            assertThat(context.containsBean("helpCommand")).isTrue();
            assertThat(context.containsBean("versionCommand")).isTrue();
            assertThat(context.containsBean("clearCommand")).isFalse();
            assertThat(context.containsBean("scriptCommand")).isFalse();

            Command versionCommand = context.getBean("versionCommand", Command.class);
            StringWriter output = new StringWriter();
            CommandContext commandContext = new CommandContext(ParsedInput.builder().commandName("version").build(),
                    new CommandRegistry(), new PrintWriter(output), null);

            ExitStatus exitStatus = versionCommand.execute(commandContext);

            assertThat(exitStatus).isEqualTo(ExitStatus.OK);
            assertThat(output.toString())
                .contains("Version: 9.9.9")
                .contains("Build Group: example.group")
                .contains("Build Artifact: example-artifact")
                .contains("Build Name: Example Shell");
        }
    }

    @Test
    void springShellPropertiesBindNestedConfigurationAndResolveUserConfigLocation() {
        Map<String, Object> properties = Map.ofEntries(
                Map.entry("spring.shell.config.location", "build/test-user-config"),
                Map.entry("spring.shell.history.enabled", "false"),
                Map.entry("spring.shell.history.name", "commands.log"),
                Map.entry("spring.shell.interactive.enabled", "false"),
                Map.entry("spring.shell.debug.enabled", "true"),
                Map.entry("spring.shell.theme.name", "dump"),
                Map.entry("spring.shell.command.help.enabled", "false"),
                Map.entry("spring.shell.command.version.show-git-short-commit-id", "true"),
                Map.entry("spring.shell.context.close", "true"));

        try (AnnotationConfigApplicationContext context = newContext(properties, UserConfigAutoConfiguration.class)) {
            SpringShellProperties shellProperties = context.getBean(SpringShellProperties.class);

            assertThat(shellProperties.getConfig().getLocation()).isEqualTo("build/test-user-config");
            assertThat(shellProperties.getHistory().isEnabled()).isFalse();
            assertThat(shellProperties.getHistory().getName()).isEqualTo("commands.log");
            assertThat(shellProperties.getInteractive().isEnabled()).isFalse();
            assertThat(shellProperties.getDebug().isEnabled()).isTrue();
            assertThat(shellProperties.getTheme().getName()).isEqualTo("dump");
            assertThat(shellProperties.getCommand().getHelp().isEnabled()).isFalse();
            assertThat(shellProperties.getCommand().getVersion().isShowGitShortCommitId()).isTrue();
            assertThat(shellProperties.getContext().isClose()).isTrue();
            assertThat(context.getBean(UserConfigPathProvider.class).provide())
                .isEqualTo(Path.of("build/test-user-config"));
        }
    }

    @Test
    void jlineAutoConfigurationCreatesShellInfrastructureWithTheming(@TempDir Path tempDirectory) {
        Map<String, Object> properties = Map.of(
                "spring.shell.config.location", tempDirectory.toString(),
                "spring.shell.history.enabled", "true",
                "spring.shell.history.name", "history.log",
                "spring.shell.interactive.enabled", "true");

        try (AnnotationConfigApplicationContext context = newContext(properties,
                CommandRegistryAutoConfiguration.class, UserConfigAutoConfiguration.class,
                JLineShellAutoConfiguration.class, JLineSupportConfiguration.class)) {
            LineReader lineReader = context.getBean(LineReader.class);
            Object historyFile = lineReader.getVariable(LineReader.HISTORY_FILE);

            assertThat(historyFile).isEqualTo(tempDirectory.resolve("history.log").toAbsolutePath());
            assertThat(context.getBean(Parser.class)).isNotNull();
            assertThat(context.getBean(PromptProvider.class).getPrompt().toString()).contains("shell:>");
            assertThat(context.getBean(Terminal.class).getType()).isNotBlank();
            assertThat(context.getBean(ThemeActive.class).get()).isIn("default", "dump");
            assertThat(context.getBean(ThemeRegistry.class)).isNotNull();
            assertThat(context.getBean(ThemeResolver.class)).isNotNull();
            assertThat(context.getBean(TemplateExecutor.class)).isNotNull();
            assertThat(context.getBean(ComponentFlow.Builder.class)).isNotNull();
            assertThat(context.getBean(TerminalUIBuilder.class)).isNotNull();
            assertThat(context.getBean(ViewComponentExecutor.class)).isNotNull();
        }
    }

    @Test
    void shellRunnerAutoConfigurationDelegatesApplicationArgumentsToShellRunner() throws Exception {
        try (AnnotationConfigApplicationContext context = newContext(Collections.emptyMap(),
                CommandRegistryAutoConfiguration.class, ShellRunnerAutoConfiguration.class,
                RecordingShellRunnerConfiguration.class)) {
            ApplicationRunner applicationRunner = context.getBean("springShellApplicationRunner", ApplicationRunner.class);
            RecordingShellRunner shellRunner = context.getBean(RecordingShellRunner.class);

            applicationRunner.run(new DefaultApplicationArguments("sample", "--name=native"));

            assertThat(shellRunner.getArguments()).containsExactly("sample", "--name=native");
        }
    }

    @Test
    void jlineCommandCompleterSuggestsNestedCommandsAndOptions(@TempDir Path tempDirectory) {
        Map<String, Object> properties = Map.of(
                "spring.shell.config.location", tempDirectory.toString(),
                "spring.shell.history.enabled", "false",
                "spring.shell.interactive.enabled", "true");

        try (AnnotationConfigApplicationContext context = newContext(properties,
                CommandRegistryAutoConfiguration.class, UserConfigAutoConfiguration.class,
                JLineShellAutoConfiguration.class, JLineSupportConfiguration.class,
                CompletionCommandConfiguration.class)) {
            CommandCompleter completer = context.getBean(CommandCompleter.class);

            assertThat(candidateValues(complete(completer, "admin ", "admin", ""))).contains("user");
            assertThat(candidateValues(complete(completer, "admin user ", "admin", "user", "")))
                .contains("--role", "-r");
            assertThat(candidateValues(complete(completer, "admin user --role=operator ",
                    "admin", "user", "--role=operator", "")))
                .doesNotContain("--role", "-r");
        }
    }

    private static List<Candidate> complete(CommandCompleter completer, String line, String... words) {
        List<Candidate> candidates = new ArrayList<>();
        List<String> parsedWords = List.of(words);
        String word = parsedWords.get(parsedWords.size() - 1);
        completer.complete(null, new CompletionLine(line, parsedWords, parsedWords.size() - 1, word, word.length()),
                candidates);
        return candidates;
    }

    private static List<String> candidateValues(List<Candidate> candidates) {
        return candidates.stream().map(Candidate::value).toList();
    }

    private record CompletionLine(String line, List<String> words, int wordIndex, String word,
            int wordCursor) implements ParsedLine {

        @Override
        public int cursor() {
            return line().length();
        }

    }

    private static AnnotationConfigApplicationContext newContext(Map<String, Object> properties,
            Class<?>... configurationClasses) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(configurationClasses);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    static class CommandConfiguration {

        @Bean
        Command programmaticCommand() {
            return Command.builder()
                .name("programmatic")
                .description("registered as a Command bean")
                .execute(commandContext -> "programmatic result");
        }

    }

    @Component
    static class GreetingCommands {

        @org.springframework.shell.core.command.annotation.Command(name = "greet", alias = "hello",
                description = "Greet a named target")
        String greet(@org.springframework.shell.core.command.annotation.Option(longName = "name",
                defaultValue = "world") String name) {
            return "Hello " + name;
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class CompletionCommandConfiguration {

        @Bean
        Command adminUserCommand() {
            return Command.builder()
                .name("admin user")
                .description("Manage shell users")
                .options(CommandOption.with()
                    .longName("role")
                    .shortName('r')
                    .description("Assigned role")
                    .type(String.class)
                    .build())
                .execute(commandContext -> "updated");
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class BuildInfoConfiguration {

        @Bean
        BuildProperties buildProperties() {
            Properties properties = new Properties();
            properties.setProperty("group", "example.group");
            properties.setProperty("artifact", "example-artifact");
            properties.setProperty("name", "Example Shell");
            properties.setProperty("version", "9.9.9");
            return new BuildProperties(properties);
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class RecordingShellRunnerConfiguration {

        @Bean
        RecordingShellRunner recordingShellRunner() {
            return new RecordingShellRunner();
        }

    }

    static class RecordingShellRunner implements ShellRunner {

        private String[] arguments = new String[0];

        @Override
        public void run(String[] args) {
            this.arguments = args.clone();
        }

        String[] getArguments() {
            return this.arguments.clone();
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class JLineSupportConfiguration {

        @Bean
        History history() {
            return new DefaultHistory();
        }

        @Bean
        TerminalCustomizer testTerminalCustomizer() {
            return builder -> builder
                .dumb(true)
                .system(false)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        }

        @Bean
        CommandParser commandParser(CommandRegistry commandRegistry) {
            return new DefaultCommandParser(commandRegistry);
        }

    }

}
