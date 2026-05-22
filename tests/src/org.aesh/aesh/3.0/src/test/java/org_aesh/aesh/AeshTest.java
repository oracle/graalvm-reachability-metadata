/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.aesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import org.aesh.command.AeshCommandRuntimeBuilder;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.CommandRuntime;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.converter.Converter;
import org.aesh.command.converter.ConverterInvocation;
import org.aesh.command.export.ExportManager;
import org.aesh.command.export.ExportPreProcessor;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.operator.OperatorType;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.aesh.command.registry.CommandRegistry;
import org.aesh.command.result.ResultHandler;
import org.aesh.command.validator.CommandValidator;
import org.aesh.command.validator.CommandValidatorException;
import org.aesh.command.validator.OptionValidator;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.command.validator.ValidatorInvocation;
import org.aesh.complete.AeshCompleteOperation;
import org.aesh.io.FileResource;
import org.aesh.io.Resource;
import org.aesh.io.filter.AllResourceFilter;
import org.aesh.io.filter.DirectoryResourceFilter;
import org.aesh.io.filter.NoDotNamesFilter;
import org.aesh.parser.LineParser;
import org.aesh.parser.ParsedLine;
import org.aesh.parser.ParserStatus;
import org.aesh.readline.terminal.formatting.TerminalString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AeshTest {
    private static RecipeExecution lastRecipeExecution;
    private static String lastAdminExecution;
    private static String lastValidatedTask;
    private static String lastTaskExecution;
    private static String lastResultHandlerEvent;

    @BeforeEach
    void resetCapturedExecutions() {
        lastRecipeExecution = null;
        lastAdminExecution = null;
        lastValidatedTask = null;
        lastTaskExecution = null;
        lastResultHandlerEvent = null;
    }

    @Test
    void annotatedCommandParsesOptionsArgumentsListsConvertersAndValidators() throws Exception {
        CommandRuntime<CommandInvocation> runtime = runtimeFor(RecipeCommand.class);

        CommandResult result = runtime.executeCommand(
                "recipe --count 3x2 --mode fast --tag dinner,quick --tag vegan --verbose flour sugar");

        assertThat(result).isEqualTo(CommandResult.SUCCESS);
        assertThat(lastRecipeExecution.count()).isEqualTo(6);
        assertThat(lastRecipeExecution.mode()).isEqualTo("fast");
        assertThat(lastRecipeExecution.verbose()).isTrue();
        assertThat(lastRecipeExecution.tags()).containsExactly("dinner", "quick", "vegan");
        assertThat(lastRecipeExecution.ingredients()).containsExactly("flour", "sugar");
    }

    @Test
    void aliasesDefaultsAndValidationAreAppliedByTheCommandRuntime() throws Exception {
        CommandRuntime<CommandInvocation> runtime = runtimeFor(RecipeCommand.class);

        CommandResult result = runtime.executeCommand("cook --tag quick oats");

        assertThat(result.isSuccess()).isTrue();
        assertThat(lastRecipeExecution.count()).isEqualTo(1);
        assertThat(lastRecipeExecution.mode()).isEqualTo("slow");
        assertThat(lastRecipeExecution.verbose()).isFalse();
        assertThat(lastRecipeExecution.tags()).containsExactly("quick");
        assertThat(lastRecipeExecution.ingredients()).containsExactly("oats");
        assertThat(runtime.commandInfo("recipe"))
                .contains("Builds a recipe")
                .contains("--count")
                .contains("--mode");

        assertThatThrownBy(() -> runtime.executeCommand("recipe --count 0 flour"))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(OptionValidatorException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void completionUsesRegisteredCommandsOptionsAndCustomOptionCompleters() throws Exception {
        CommandRuntime<CommandInvocation> runtime = runtimeFor(RecipeCommand.class);

        AeshCompleteOperation commandCompletion = new AeshCompleteOperation("rec", "rec".length());
        runtime.complete(commandCompletion);
        assertThat(candidateCharacters(commandCompletion)).contains("recipe");

        String optionInput = "recipe --co";
        AeshCompleteOperation optionCompletion = new AeshCompleteOperation(optionInput, optionInput.length());
        runtime.complete(optionCompletion);
        assertThat(candidateCharacters(optionCompletion)).contains("--count=");

        String valueInput = "recipe --mode f";
        AeshCompleteOperation valueCompletion = new AeshCompleteOperation(valueInput, valueInput.length());
        runtime.complete(valueCompletion);
        assertThat(candidateCharacters(valueCompletion)).contains("fast");
    }

    @Test
    void groupCommandDispatchesToAnnotatedChildCommands() throws Exception {
        CommandRuntime<CommandInvocation> runtime = runtimeFor(AdminCommand.class);

        CommandResult result = runtime.executeCommand("admin status --name production");

        assertThat(result).isEqualTo(CommandResult.SUCCESS);
        assertThat(lastAdminExecution).isEqualTo("status:production");
        assertThat(runtime.getCommandRegistry().getAllCommandNames()).contains("admin");
        assertThat(runtime.commandInfo("admin")).contains("Administrative commands").contains("status");
    }

    @Test
    void commandValidatorAndResultHandlerObserveCommandOutcomes() throws Exception {
        CommandRuntime<CommandInvocation> runtime = runtimeFor(ValidatedTaskCommand.class);

        CommandResult result = runtime.executeCommand("task --name deploy");

        assertThat(result).isEqualTo(CommandResult.SUCCESS);
        assertThat(lastValidatedTask).isEqualTo("deploy");
        assertThat(lastTaskExecution).isEqualTo("deploy");
        assertThat(lastResultHandlerEvent).isEqualTo("success");

        lastValidatedTask = null;
        lastTaskExecution = null;
        lastResultHandlerEvent = null;

        assertThatThrownBy(() -> runtime.executeCommand("task --name forbidden"))
                .isInstanceOf(CommandValidatorException.class)
                .hasMessageContaining("forbidden");
        assertThat(lastValidatedTask).isEqualTo("forbidden");
        assertThat(lastTaskExecution).isNull();
        assertThat(lastResultHandlerEvent).isEqualTo("validation:CommandValidatorException");
    }

    @Test
    void lineParserHandlesQuotingCursorSelectionAndOperators() {
        ParsedLine parsedLine = new LineParser().parseLine("recipe --mode \"very fast\" ingredient", 18);

        assertThat(parsedLine.status()).isEqualTo(ParserStatus.OK);
        assertThat(parsedLine.words()).extracting(word -> word.word())
                .containsExactly("recipe", "--mode", "very fast", "ingredient");
        assertThat(parsedLine.selectedWord().word()).isEqualTo("very fast");
        assertThat(parsedLine.wordCursor()).isPositive();

        List<ParsedLine> pipeline = new LineParser()
                .input("recipe flour\\ sugar | admin status")
                .operators(EnumSet.of(OperatorType.PIPE))
                .parseWithOperators();
        assertThat(pipeline).hasSize(2);
        assertThat(pipeline.get(0).operator()).isEqualTo(OperatorType.PIPE);
        assertThat(pipeline.get(0).words()).extracting(word -> word.word()).containsExactly("recipe", "flour sugar");
        assertThat(pipeline.get(1).operator()).isEqualTo(OperatorType.NONE);
        assertThat(pipeline.get(1).words()).extracting(word -> word.word()).containsExactly("admin", "status");

        ParsedLine invalidLine = new LineParser().parseLine("recipe \"unterminated");
        assertThat(invalidLine.status()).isEqualTo(ParserStatus.UNCLOSED_QUOTE);
    }

    @Test
    void exportManagerExpandsVariablesAndProvidesCompletionNames(@TempDir Path tempDir) {
        ExportManager manager = new ExportManager(tempDir.resolve("exports").toFile());

        assertThat(manager.addVariable("export BASE=/opt/aesh")).isNull();
        assertThat(manager.addVariable("export BIN=$BASE/bin")).isNull();
        assertThat(manager.addVariable("export MODE=fast")).isNull();

        assertThat(manager.keys()).containsExactlyInAnyOrder("BASE", "BIN", "MODE");
        assertThat(manager.getValue("BIN")).isEqualTo("/opt/aesh/bin");
        assertThat(manager.getValue("run-${BIN}:$MODE")).isEqualTo("run-/opt/aesh/bin:fast");
        assertThat(manager.getValueIgnoreCase("mode")).isEqualTo("fast");
        assertThat(manager.findAllMatchingKeys("$B")).containsExactlyInAnyOrder("$BASE", "$BIN");
        assertThat(manager.getAllNamesWithEquals()).contains("BASE=", "BIN=", "MODE=");
        assertThat(manager.listAllVariables())
                .contains("BASE=/opt/aesh")
                .contains("BIN=/opt/aesh/bin")
                .contains("MODE=fast");

        ExportPreProcessor preProcessor = new ExportPreProcessor(manager);
        assertThat(preProcessor.apply("$BIN/tool --mode=$MODE")).contains("/opt/aesh/bin/tool --mode=fast");
    }

    @Test
    void fileResourcesReadWriteListFilterAndCopy(@TempDir Path tempDir) throws Exception {
        Path visibleDirectory = Files.createDirectory(tempDir.resolve("visible"));
        Path hiddenDirectory = Files.createDirectory(tempDir.resolve(".hidden"));
        Path sourceFile = tempDir.resolve("source.txt");
        Files.writeString(sourceFile, "aesh resource", StandardCharsets.UTF_8);
        Files.writeString(visibleDirectory.resolve("nested.txt"), "nested", StandardCharsets.UTF_8);
        Files.writeString(hiddenDirectory.resolve("secret.txt"), "secret", StandardCharsets.UTF_8);

        Resource root = new FileResource(tempDir);
        Resource source = new FileResource(sourceFile);
        Resource target = new FileResource(tempDir.resolve("target.txt"));

        assertThat(root.exists()).isTrue();
        assertThat(root.isDirectory()).isTrue();
        assertThat(source.isLeaf()).isTrue();
        assertThat(resourceNames(root.list(new AllResourceFilter())))
                .contains("visible", ".hidden", "source.txt");
        assertThat(resourceNames(root.list(new DirectoryResourceFilter())))
                .contains("visible", ".hidden")
                .doesNotContain("source.txt");
        assertThat(resourceNames(root.list(new NoDotNamesFilter())))
                .contains("visible", "source.txt")
                .doesNotContain(".hidden");

        source.copy(target);
        try (InputStream inputStream = target.read()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("aesh resource");
        }

        Resource created = root.newInstance(tempDir.resolve("created.txt").toString());
        try (OutputStream outputStream = created.write(false)) {
            outputStream.write("created".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(Files.readString(tempDir.resolve("created.txt"), StandardCharsets.UTF_8)).isEqualTo("created");
    }

    private static CommandRuntime<CommandInvocation> runtimeFor(Class<? extends Command> command) throws Exception {
        CommandRegistry<CommandInvocation> registry = AeshCommandRegistryBuilder.<CommandInvocation>builder()
                .command(command)
                .create();
        return AeshCommandRuntimeBuilder.<CommandInvocation>builder()
                .commandRegistry(registry)
                .build();
    }

    private static List<String> candidateCharacters(AeshCompleteOperation operation) {
        return operation.getCompletionCandidates().stream()
                .map(TerminalString::getCharacters)
                .collect(Collectors.toList());
    }

    private static List<String> resourceNames(List<Resource> resources) {
        return resources.stream()
                .map(Resource::getName)
                .collect(Collectors.toList());
    }

    @CommandDefinition(
            name = "recipe",
            aliases = {"cook"},
            description = "Builds a recipe from command line ingredients",
            generateHelp = true)
    public static class RecipeCommand implements Command<CommandInvocation> {
        @Option(
                name = "count",
                shortName = 'c',
                description = "Number of batches",
                defaultValue = {"1"},
                converter = MultiplierConverter.class,
                validator = PositiveIntegerValidator.class)
        private Integer count;

        @Option(
                name = "mode",
                description = "Preparation mode",
                defaultValue = {"slow"},
                completer = ModeCompleter.class)
        private String mode;

        @Option(name = "verbose", shortName = 'v', description = "Enable verbose output", hasValue = false)
        private boolean verbose;

        @OptionList(name = "tag", shortName = 't', description = "Recipe tags", valueSeparator = ',')
        private List<String> tags;

        @Arguments(description = "Ingredients")
        private List<String> ingredients;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            lastRecipeExecution = new RecipeExecution(count, mode, verbose, tags, ingredients);
            return CommandResult.SUCCESS;
        }
    }

    @GroupCommandDefinition(
            name = "admin",
            description = "Administrative commands",
            groupCommands = {AdminStatusCommand.class})
    public static class AdminCommand implements Command<CommandInvocation> {
        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            lastAdminExecution = "admin";
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(name = "status", description = "Shows status for a named environment")
    public static class AdminStatusCommand implements Command<CommandInvocation> {
        @Option(name = "name", required = true, description = "Environment name")
        private String name;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            lastAdminExecution = "status:" + name;
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(
            name = "task",
            description = "Runs a validated task",
            validator = TaskCommandValidator.class,
            resultHandler = TaskResultHandler.class)
    public static class ValidatedTaskCommand implements Command<CommandInvocation> {
        @Option(name = "name", required = true, description = "Task name")
        private String name;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            lastTaskExecution = name;
            return CommandResult.SUCCESS;
        }
    }

    public static class TaskCommandValidator implements CommandValidator<ValidatedTaskCommand, CommandInvocation> {
        @Override
        public void validate(ValidatedTaskCommand command) throws CommandValidatorException {
            lastValidatedTask = command.name;
            if ("forbidden".equals(command.name)) {
                throw new CommandValidatorException("forbidden task names are rejected");
            }
        }
    }

    public static class TaskResultHandler implements ResultHandler {
        @Override
        public void onSuccess() {
            lastResultHandlerEvent = "success";
        }

        @Override
        public void onFailure(CommandResult commandResult) {
            lastResultHandlerEvent = "failure:" + commandResult.getResultValue();
        }

        @Override
        public void onValidationFailure(CommandResult commandResult, Exception exception) {
            lastResultHandlerEvent = "validation:" + exception.getClass().getSimpleName();
        }

        @Override
        public void onExecutionFailure(CommandResult commandResult, CommandException exception) {
            lastResultHandlerEvent = "execution:" + exception.getClass().getSimpleName();
        }
    }

    public static class MultiplierConverter implements Converter<Integer, ConverterInvocation> {
        @Override
        public Integer convert(ConverterInvocation invocation) throws OptionValidatorException {
            String input = invocation.getInput();
            if (input.contains("x")) {
                String[] factors = input.split("x", -1);
                int product = 1;
                for (String factor : factors) {
                    product *= Integer.parseInt(factor);
                }
                return product;
            }
            return Integer.valueOf(input);
        }
    }

    public static class PositiveIntegerValidator implements OptionValidator<ValidatorInvocation<Integer, RecipeCommand>> {
        @Override
        public void validate(ValidatorInvocation<Integer, RecipeCommand> invocation) throws OptionValidatorException {
            if (invocation.getValue() == null || invocation.getValue() <= 0) {
                throw new OptionValidatorException("count must be positive");
            }
        }
    }

    public static class ModeCompleter implements OptionCompleter<CompleterInvocation> {
        @Override
        public void complete(CompleterInvocation invocation) {
            invocation.setCompleterValues(List.of("fast", "slow", "balanced"));
        }
    }

    private record RecipeExecution(
            Integer count,
            String mode,
            boolean verbose,
            List<String> tags,
            List<String> ingredients) {
    }
}
