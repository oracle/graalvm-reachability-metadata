/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Closure;
import groovyjarjarpicocli.CommandLine;
import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandLineInnerDefaultFactoryTest {
    @Test
    void instantiatesCommandClassesWithPublicAndNonPublicConstructors() {
        CommandLine publicCommand = new CommandLine(PublicCommand.class);
        assertEquals(10, publicCommand.execute("--value", "5"));
        assertEquals(Integer.valueOf(10), publicCommand.getExecutionResult());
        CommandLine privateCommand = new CommandLine(PrivateCommand.class);
        assertEquals(0, privateCommand.execute());
        assertEquals("private", privateCommand.getExecutionResult());
    }

    @Test
    void usesAClosureAsAFactory() throws Exception {
        Callable<?> product = CommandLine.defaultFactory().create(CommandFactory.class);
        assertEquals("created", product.call());
    }

    @Command(name = "double")
    public static class PublicCommand implements Callable<Integer> {
        @Option(names = "--value")
        public int value;

        @Override
        public Integer call() {
            return value * 2;
        }
    }

    @Command(name = "private")
    public static class PrivateCommand implements Callable<String> {
        private PrivateCommand() {
        }

        @Override
        public String call() {
            return "private";
        }
    }

    public static class CommandFactory extends Closure<Callable<String>> {
        public CommandFactory(Object owner, Object thisObject) {
            super(owner, thisObject);
        }

        public Callable<String> doCall() {
            return () -> "created";
        }
    }
}
