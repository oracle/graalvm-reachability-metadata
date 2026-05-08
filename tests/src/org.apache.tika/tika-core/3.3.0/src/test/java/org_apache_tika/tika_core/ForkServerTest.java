/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.fork.ForkParser;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;

public class ForkServerTest {

    @Test
    public void forkParserInvokesParserThroughForkServer() throws Exception {
        ForkParser parser = new ForkParser(ForkParser.class.getClassLoader(), EmptyParser.INSTANCE);
        parser.setPoolSize(1);
        parser.setServerPulseMillis(50L);
        parser.setServerParseTimeoutMillis(5_000L);
        parser.setServerWaitTimeoutMillis(250L);
        parser.setJavaCommand(javaCommand());
        try {
            parser.parse(
                    new ByteArrayInputStream("plain text".getBytes(UTF_8)),
                    new DefaultHandler(),
                    new Metadata(),
                    new ParseContext());
            Thread.sleep(1_000L);
        } catch (TikaException exception) {
            assertThat(exception.getMessage())
                    .isEqualTo("Failed to communicate with a forked parser process."
                            + " The process has most likely crashed due to some error"
                            + " like running out of memory. A new process will be"
                            + " started for the next parsing request.");
            assertThat(exception).hasCauseInstanceOf(IOException.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            parser.close();
        }
    }

    private static List<String> javaCommand() {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        jacocoAgentArgument().ifPresent(command::add);
        command.add("-Xmx64m");
        command.add("-Djava.awt.headless=true");
        return command;
    }

    private static Optional<String> jacocoAgentArgument() {
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.startsWith("-javaagent:") && argument.toLowerCase().contains("jacoco")) {
                return Optional.of(withForkServerCoverage(argument));
            }
        }
        return Optional.empty();
    }

    private static String withForkServerCoverage(String argument) {
        int optionStart = argument.indexOf('=');
        if (optionStart < 0) {
            return argument + "=append=true,includes=org.apache.tika.fork.*";
        }
        String agentPath = argument.substring(0, optionStart);
        String options = argument.substring(optionStart + 1);
        List<String> rewrittenOptions = new ArrayList<>();
        boolean hasAppend = false;
        boolean hasIncludes = false;
        for (String option : options.split(",")) {
            if (option.startsWith("append=")) {
                rewrittenOptions.add("append=true");
                hasAppend = true;
            } else if (option.startsWith("includes=")) {
                rewrittenOptions.add("includes=org.apache.tika.fork.*");
                hasIncludes = true;
            } else if (!option.isEmpty()) {
                rewrittenOptions.add(option);
            }
        }
        if (!hasAppend) {
            rewrittenOptions.add("append=true");
        }
        if (!hasIncludes) {
            rewrittenOptions.add("includes=org.apache.tika.fork.*");
        }
        return agentPath + "=" + String.join(",", rewrittenOptions);
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }
}
