/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.builtins.Completers.OptDesc;
import org.jline.builtins.ConfigurationPath;
import org.jline.console.ArgDesc;
import org.jline.console.CmdDesc;
import org.jline.console.CmdLine;
import org.jline.console.CommandInput;
import org.jline.console.CommandMethods;
import org.jline.console.CommandRegistry;
import org.jline.console.ConsoleEngine.ExecutionResult;
import org.jline.console.Printer;
import org.jline.console.ScriptEngine;
import org.jline.console.SystemRegistry;
import org.jline.console.impl.ConsoleEngineImpl;
import org.jline.console.impl.JlineCommandRegistry;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReader.SuggestionType;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jline.utils.AttributedString;
import org.jline.widget.AutopairWidgets;
import org.jline.widget.AutosuggestionWidgets;
import org.jline.widget.Widgets;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class JLineConsoleTests {

    @Test
    public void commandDescriptorsParseUsageTextAndExposeOptionMetadata() {
        String help = "demo -  format data\n"
                + "Usage: demo [OPTIONS] FILE\n"
                + "  -f --file=FILE               File to read\n"
                + "     --verbose                 Enable extra output\n";

        List<String> info = JlineCommandRegistry.compileCommandInfo(help);
        assertEquals(Collections.singletonList("format data"), info);

        CmdDesc description = JlineCommandRegistry.compileCommandDescription(help);
        assertTrue(description.isValid());
        assertTrue(description.isCommand());
        assertEquals(1, description.getArgsDesc().size());
        assertEquals("", description.getArgsDesc().get(0).getName());
        assertTrue(description.optionWithValue("--file"));
        assertFalse(description.optionWithValue("--verbose"));
        assertEquals("File to read", description.optionDescription("-f --file=FILE").toString());

        List<OptDesc> options = JlineCommandRegistry.compileCommandOptions(help);
        assertEquals(2, options.size());
        assertEquals("-f", options.get(0).shortOption());
        assertEquals("--file", options.get(0).longOption());
        assertEquals("File to read", options.get(0).description());
        assertNull(options.get(1).shortOption());
        assertEquals("--verbose", options.get(1).longOption());
    }

    @Test
    public void descriptorAndCommandLineValueObjectsDefensivelyCopyInputs() {
        List<AttributedString> main = new ArrayList<>();
        main.add(new AttributedString("main help"));
        List<ArgDesc> args = new ArrayList<>(ArgDesc.doArgNames(Arrays.asList("source", "target")));
        Map<String, List<AttributedString>> options = new HashMap<>();
        options.put("--force", new ArrayList<>(Collections.singletonList(new AttributedString("Overwrite target"))));

        CmdDesc description = new CmdDesc(main, args, options);
        main.clear();
        args.clear();
        options.clear();

        assertEquals(1, description.getMainDesc().size());
        assertEquals("main help", description.getMainDesc().get(0).toString());
        assertEquals(2, description.getArgsDesc().size());
        assertEquals("source", description.getArgsDesc().get(0).getName());
        assertEquals("Overwrite target", description.optionDescription("--force").toString());

        CmdLine line = new CmdLine("demo --force input", "demo", " --force input",
                Arrays.asList("demo", "--force", "input"), CmdLine.DescriptionType.COMMAND);
        assertEquals("demo --force input", line.getLine());
        assertEquals("demo", line.getHead());
        assertEquals(" --force input", line.getTail());
        assertEquals(Arrays.asList("demo", "--force", "input"), line.getArgs());
        assertEquals(CmdLine.DescriptionType.COMMAND, line.getDescriptionType());
    }

    @Test
    public void commandRegistryInvokesAliasesAndAggregatesCompleters() throws Exception {
        SampleRegistry registry = new SampleRegistry();
        registry.alias("repeat", "echo");

        assertTrue(registry.hasCommand("echo"));
        assertTrue(registry.hasCommand("repeat"));
        assertEquals(Collections.singletonMap("repeat", "echo"), registry.commandAliases());

        CommandRegistry.CommandSession session = new CommandRegistry.CommandSession();
        Object result = registry.invoke(session, "repeat", "alpha", 42);
        assertEquals("repeat:[alpha, 42]", result);
        assertSame(System.in, registry.lastInput.session().in());
        assertArrayEquals(new String[] {"alpha", "42"}, registry.lastInput.args());
        assertArrayEquals(new Object[] {"alpha", 42}, registry.lastInput.xargs());

        assertTrue(registry.compileCompleters().getCompleters().containsKey("echo"));
        assertTrue(CommandRegistry.aggregateCompleters(registry).getCompleters().containsKey("echo"));
        assertTrue(CommandRegistry.compileCompleters(registry).isCompiled());
    }

    @Test
    public void systemRegistryDispatchesRegisteredCommandsAndLocalHelp() throws Exception {
        SampleRegistry registry = new SampleRegistry();
        Terminal terminal = newTerminal();
        Path config = Files.createTempDirectory("jline-console-config");
        SystemRegistryImpl systemRegistry = new SystemRegistryImpl(new DefaultParser(), terminal,
                config::toAbsolutePath, new ConfigurationPath(config, config));

        try {
            systemRegistry.setCommandRegistries(registry);
            assertSame(systemRegistry, SystemRegistry.get());
            assertTrue(systemRegistry.hasCommand("echo"));
            assertTrue(systemRegistry.hasCommand("help"));
            assertTrue(systemRegistry.commandNames().contains("exit"));
            assertEquals("echo:[one, two]", systemRegistry.invoke("echo", "one", "two"));

            List<String> helpInfo = systemRegistry.commandInfo("help");
            assertFalse(helpInfo.isEmpty());
            assertTrue(helpInfo.get(0).contains("command help"));
        } finally {
            systemRegistry.close();
            SystemRegistry.remove();
        }
    }

    @Test
    public void consoleEngineManagesVariablesOptionsAliasesAndPostProcessing() throws Exception {
        FakeScriptEngine scriptEngine = new FakeScriptEngine();
        CapturingPrinter printer = new CapturingPrinter();
        Path config = Files.createTempDirectory("jline-console-engine");
        ConsoleEngineImpl engine = new ConsoleEngineImpl(scriptEngine, printer,
                config::toAbsolutePath, new ConfigurationPath(config, config));

        engine.putVariable("name", "JLine");
        engine.putVariable("home", config.toString());
        assertTrue(engine.hasVariable("name"));
        assertEquals("JLine", engine.getVariable("name"));

        Map<String, Object> consoleOptions = new HashMap<>();
        consoleOptions.put("trace", 2);
        scriptEngine.put("CONSOLE_OPTIONS", consoleOptions);
        assertEquals(Integer.valueOf(2), engine.consoleOption("trace", 0));
        assertEquals("fallback", engine.consoleOption("missing", "fallback"));

        assertEquals("[true,5,variable,\"literal text\"]",
                engine.expandToList(Arrays.asList("true", "5", "$variable", "literal text")));
        Object[] expanded = engine.expandParameters(new String[] {"$name", "${home}/child", "plain"});
        assertArrayEquals(new Object[] {"JLine", config + "/child", "plain"}, expanded);

        CommandRegistry.CommandSession session = new CommandRegistry.CommandSession();
        engine.invoke(session, "alias", "ll", "show", "name");
        assertTrue(engine.hasAlias("ll"));
        assertEquals("show name", engine.getAlias("ll"));
        assertEquals("show name", engine.invoke(session, "alias", "ll"));
        engine.invoke(session, "unalias", "ll");
        assertFalse(engine.hasAlias("ll"));

        ExecutionResult executionResult = engine.postProcess(42);
        assertEquals(0, executionResult.status());
        assertEquals(42, executionResult.result());
        assertEquals(42, scriptEngine.get("_executionResult"));
    }

    @Test
    public void widgetsRegisterAliasesAndOperateOnLineReaderBuffer() throws IOException {
        Terminal terminal = newTerminal();
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new DefaultParser())
                .build();
        TestWidgets widgets = new TestWidgets(reader);
        AtomicInteger calls = new AtomicInteger();

        widgets.addWidget("_append-marker", () -> {
            calls.incrementAndGet();
            widgets.putString("!");
            return true;
        });
        widgets.aliasWidget("_append-marker", "_alias-marker");

        assertTrue(widgets.existsWidget("_append-marker"));
        assertTrue(widgets.existsWidget("_alias-marker"));
        assertEquals("_append-marker", widgets.getWidget("_alias-marker"));

        widgets.putString("command value");
        assertEquals(Arrays.asList("command", "value"), widgets.args());
        reader.getWidgets().get("_alias-marker").apply();
        assertEquals(1, calls.get());
        assertEquals("command value!", widgets.buffer().toString());
        assertSame(reader.getParser(), widgets.parser());
        assertNotNull(widgets.getKeyMap());
    }

    @Test
    public void autopairWidgetsInsertMatchingDelimitersWhileReadingInput() throws IOException {
        String input = "(alpha\n(beta)\n\"quoted\"\n{block\n";
        Terminal terminal = newTerminal(input);
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new DefaultParser())
                .build();
        new AutopairWidgets(reader, true).enable();

        assertEquals("(alpha)", reader.readLine());
        assertEquals("(beta)", reader.readLine());
        assertEquals("\"quoted\"", reader.readLine());
        assertEquals("{block}", reader.readLine());
    }

    @Test
    public void autosuggestionWidgetsAcceptTailTipsIntoTheLineBuffer() throws IOException {
        Terminal terminal = newTerminal();
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new DefaultParser())
                .build();
        AutosuggestionWidgets widgets = new AutosuggestionWidgets(reader);

        assertEquals(SuggestionType.NONE, reader.getAutosuggestion());
        widgets.enable();
        assertEquals(SuggestionType.HISTORY, reader.getAutosuggestion());

        widgets.putString("git");
        widgets.setTailTip(" status");
        assertTrue(widgets.autosuggestForwardChar());
        assertEquals("git status", widgets.buffer().toString());

        widgets.setTailTip(" --short");
        assertTrue(widgets.autosuggestEndOfLine());
        assertEquals("git status --short", widgets.buffer().toString());

        widgets.disable();
        assertEquals(SuggestionType.NONE, reader.getAutosuggestion());
    }

    private static Terminal newTerminal() throws IOException {
        return newTerminal("");
    }

    private static Terminal newTerminal(String input) throws IOException {
        return new DumbTerminal("test", "dumb", new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream(), StandardCharsets.UTF_8);
    }

    private static class SampleRegistry extends JlineCommandRegistry {
        private CommandInput lastInput;

        SampleRegistry() {
            Map<String, CommandMethods> commands = new LinkedHashMap<>();
            commands.put("echo", new CommandMethods(input -> {
                lastInput = input;
                return input.command() + ":" + Arrays.toString(input.args());
            }, command -> Collections.singletonList(new StringsCompleter("one", "two"))));
            registerCommands(commands);
        }

        @Override
        public List<String> commandInfo(String command) {
            return Collections.singletonList("echo command arguments");
        }

        @Override
        public CmdDesc commandDescription(List<String> args) {
            Map<String, List<AttributedString>> options = new HashMap<>();
            options.put("--upper", Collections.singletonList(new AttributedString("Upper-case output")));
            return new CmdDesc(Collections.singletonList(new AttributedString("Usage: echo [OPTIONS] ARG")),
                    ArgDesc.doArgNames(Collections.singletonList("ARG")), options);
        }
    }

    private static class CapturingPrinter implements Printer {
        private final List<Object> printed = new ArrayList<>();

        @Override
        public void println(Object object) {
            printed.add(object);
        }

        @Override
        public void println(Map<String, Object> options, Object object) {
            printed.add(object);
        }

        @Override
        public Exception prntCommand(CommandInput input) {
            printed.addAll(Arrays.asList(input.xargs()));
            return null;
        }

        @Override
        public boolean refresh() {
            return true;
        }
    }

    private static class FakeScriptEngine implements ScriptEngine {
        private final Map<String, Object> variables = new HashMap<>();
        private final Map<Path, Object> persisted = new HashMap<>();

        @Override
        public String getEngineName() {
            return "FakeScriptEngine";
        }

        @Override
        public Collection<String> getExtensions() {
            return Collections.singletonList("fake");
        }

        @Override
        public Completer getScriptCompleter() {
            return NullCompleter.INSTANCE;
        }

        @Override
        public boolean hasVariable(String name) {
            return variables.containsKey(name);
        }

        @Override
        public void put(String name, Object object) {
            variables.put(name, object);
        }

        @Override
        public Object get(String name) {
            return variables.get(name);
        }

        @Override
        public Map<String, Object> find(String name) {
            Map<String, Object> matches = new HashMap<>();
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                if (name == null || entry.getKey().contains(name)) {
                    matches.put(entry.getKey(), entry.getValue());
                }
            }
            return matches;
        }

        @Override
        public void del(String... names) {
            for (String name : names) {
                variables.remove(name);
            }
        }

        @Override
        public String toJson(Object object) {
            return String.valueOf(object);
        }

        @Override
        public String toString(Object object) {
            return String.valueOf(object);
        }

        @Override
        public Map<String, Object> toMap(Object object) {
            if (object instanceof Map) {
                return new HashMap<>((Map<String, Object>) object);
            }
            return Collections.singletonMap("value", object);
        }

        @Override
        public Object deserialize(String value, String format) {
            if ("null".equals(value)) {
                return null;
            }
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                return Boolean.valueOf(value);
            }
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException ignored) {
                return value;
            }
        }

        @Override
        public List<String> getSerializationFormats() {
            return Collections.singletonList("TEXT");
        }

        @Override
        public List<String> getDeserializationFormats() {
            return Collections.singletonList("TEXT");
        }

        @Override
        public void persist(Path file, Object object) {
            persisted.put(file, object);
        }

        @Override
        public void persist(Path file, Object object, String format) {
            persist(file, object);
        }

        @Override
        public Object execute(String script) {
            if ("_executionResult ? 0 : 1".equals(script)) {
                return variables.get("_executionResult") == null ? 1 : 0;
            }
            if (script.contains(" = ")) {
                String[] parts = script.split(" = ", 2);
                variables.put(parts[0], variables.get("_executionResult"));
                return variables.get(parts[0]);
            }
            return "executed:" + script;
        }

        @Override
        public Object execute(File script, Object[] args) {
            return script.getName() + Arrays.toString(args);
        }

        @Override
        public Object execute(Object script, Object... args) {
            return String.valueOf(script) + Arrays.toString(args);
        }
    }

    private static class TestWidgets extends Widgets {
        TestWidgets(LineReader reader) {
            super(reader);
        }
    }
}
