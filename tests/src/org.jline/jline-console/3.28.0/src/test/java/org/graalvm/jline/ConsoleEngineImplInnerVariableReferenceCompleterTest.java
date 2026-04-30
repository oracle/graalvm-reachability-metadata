/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.builtins.ConfigurationPath;
import org.jline.console.CommandInput;
import org.jline.console.Printer;
import org.jline.console.ScriptEngine;
import org.jline.console.impl.ConsoleEngineImpl;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser.ParseContext;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.SystemCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public class ConsoleEngineImplInnerVariableReferenceCompleterTest {

    @Test
    public void slurpCompletionExposesBeanGetterIdentifiersForVariableReferences() throws Exception {
        FakeScriptEngine scriptEngine = new FakeScriptEngine();
        scriptEngine.put("profile", new Profile("Ada"));
        Path config = Files.createTempDirectory("jline-console-variable-reference");
        ConsoleEngineImpl engine = new ConsoleEngineImpl(scriptEngine, new NoOpPrinter(),
                config::toAbsolutePath, new ConfigurationPath(config, config));
        LineReader reader = LineReaderBuilder.builder()
                .terminal(newTerminal())
                .parser(new DefaultParser())
                .build();
        SystemCompleter completer = engine.compileCompleters();
        completer.compile();

        List<Candidate> candidates = complete(completer, reader, "slurp ${profile}.");
        List<String> values = candidates.stream().map(Candidate::value).collect(Collectors.toList());

        assertTrue(values.contains("${profile}.class"));
        assertTrue(values.contains("${profile}.name"));
    }

    private static List<Candidate> complete(Completer completer, LineReader reader, String line) {
        ParsedLine parsedLine = reader.getParser().parse(line, line.length(), ParseContext.COMPLETE);
        List<Candidate> candidates = new ArrayList<>();
        completer.complete(reader, parsedLine, candidates);
        return candidates;
    }

    private static Terminal newTerminal() throws IOException {
        return new DumbTerminal("test", "dumb", new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(), StandardCharsets.UTF_8);
    }

    public static class Profile {
        private final String name;

        public Profile(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static class NoOpPrinter implements Printer {
        @Override
        public void println(Object object) {
        }

        @Override
        public void println(Map<String, Object> options, Object object) {
        }

        @Override
        public Exception prntCommand(CommandInput input) {
            return null;
        }

        @Override
        public boolean refresh() {
            return true;
        }
    }

    private static class FakeScriptEngine implements ScriptEngine {
        private final Map<String, Object> variables = new HashMap<>();

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
            if (name == null || name.isEmpty()) {
                return new HashMap<>(variables);
            }
            Map<String, Object> matches = new HashMap<>();
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                if (entry.getKey().contains(name)) {
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
        @SuppressWarnings("unchecked")
        public Map<String, Object> toMap(Object object) {
            if (object instanceof Map) {
                return new HashMap<>((Map<String, Object>) object);
            }
            return Collections.singletonMap("value", object);
        }

        @Override
        public Object deserialize(String value, String format) {
            return value;
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
        }

        @Override
        public void persist(Path file, Object object, String format) {
        }

        @Override
        public Object execute(String script) {
            return variables.get(script);
        }

        @Override
        public Object execute(File script, Object[] args) {
            return script.getName();
        }

        @Override
        public Object execute(Object script, Object... args) {
            return script;
        }
    }
}
