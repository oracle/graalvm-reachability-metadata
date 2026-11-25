/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarpicocli.CommandLine;
import groovyjarjarpicocli.CommandLine.Option;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandLineInnerInterpreterTest {
    @Test
    void collectsRepeatedOptionsIntoArraysAndParsesOptionalBooleans() {
        Options options = new Options();
        new CommandLine(options).parseArgs("--numbers", "3,5", "--numbers", "8", "--enabled");
        assertArrayEquals(new Integer[]{3, 5, 8}, options.numbers);
        assertEquals(Optional.of(true), options.enabled);
    }

    @Test
    void enablesAnInitializedOptionalBoolean() {
        Toggle options = new Toggle();
        new CommandLine(options).parseArgs("--active");
        assertEquals(Optional.of(true), options.active);
    }

    @Test
    @ResourceLock("java.lang.System.in")
    @ResourceLock("java.lang.System.out")
    void readsAnInteractiveSecretFromRedirectedInput() throws Exception {
        InputStream originalInput = System.in;
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ByteArrayInputStream input = new ByteArrayInputStream("parcel-token\n".getBytes(StandardCharsets.UTF_8));
             PrintStream prompt = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setIn(input);
            System.setOut(prompt);
            SecretOptions options = new SecretOptions();
            new CommandLine(options).parseArgs("--token");
            assertArrayEquals("parcel-token".toCharArray(), options.token);
        } finally {
            System.setIn(originalInput);
            System.setOut(originalOutput);
        }
        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Token: "));
        assertFalse(text.contains("parcel-token"));
    }

    public static class SecretOptions {
        @Option(names = "--token", interactive = true, arity = "0", prompt = "Token: ", echo = false)
        public char[] token;
    }

    public static class Toggle {
        @Option(names = "--active")
        public Optional<Boolean> active = Optional.of(false);
    }

    public static class Options {
        @Option(names = "--numbers", split = ",")
        public Integer[] numbers;

        @Option(names = "--enabled")
        public Optional<Boolean> enabled;
    }
}
