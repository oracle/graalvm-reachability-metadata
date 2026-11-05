/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarpicocli.AutoComplete;
import groovyjarjarpicocli.CommandLine;
import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AutoCompleteInnerAppTest {
    @TempDir
    Path directory;

    @Test
    void generatesACompletionScriptForACommandUsingItsConfiguredFactory() throws Exception {
        Factory.creations.set(0);
        Path script = directory.resolve("delivery-completion.sh");
        AutoComplete.main("--factory", Factory.class.getName(), "--completionScript", script.toString(), Delivery.class.getName());
        String completion = Files.readString(script);
        assertTrue(completion.contains("delivery"));
        assertTrue(completion.contains("--destination"));
        assertEquals(1, Factory.creations.get());
    }

    public static class Factory implements CommandLine.IFactory {
        public static final AtomicInteger creations = new AtomicInteger();

        @Override
        public <T> T create(Class<T> type) throws Exception {
            if (type == Delivery.class) {
                creations.incrementAndGet();
                return type.cast(new Delivery());
            }
            return CommandLine.defaultFactory().create(type);
        }
    }

    @Command(name = "delivery")
    public static class Delivery {
        @Option(names = "--destination")
        public String destination;
    }
}
