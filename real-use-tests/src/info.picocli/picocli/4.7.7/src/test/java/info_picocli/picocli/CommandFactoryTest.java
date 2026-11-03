/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises picocli's command factory subsystem for dependency-injected CLI commands.
public class CommandFactoryTest {
    @Test
    public void createsSubcommandWithApplicationService() {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(
                new OrdersCommand(), new ApplicationFactory(new OrderService()));
        commandLine.setOut(new PrintWriter(output));
        commandLine.setErr(new PrintWriter(error));

        int exitCode = commandLine.execute("total", "--customer", "acme");

        assertEquals(CommandLine.ExitCode.OK, exitCode, error.toString());
        assertEquals("customer=acme\ntotal=42\n", output.toString());
        assertEquals("", error.toString());
    }

    @Command(name = "orders", subcommands = TotalCommand.class)
    static class OrdersCommand implements Runnable {
        @Override
        public void run() {
            // The application exposes its work through subcommands.
        }
    }

    @Command(name = "total", description = "Show the order total for a customer.")
    static class TotalCommand implements Runnable {
        private final OrderService orderService;

        @Spec
        CommandSpec spec;

        @Option(names = "--customer", required = true, description = "Customer account.")
        String customer;

        TotalCommand(OrderService orderService) {
            this.orderService = orderService;
        }

        @Override
        public void run() {
            PrintWriter out = spec.commandLine().getOut();
            out.println("customer=" + customer);
            out.println("total=" + orderService.totalFor(customer));
        }
    }

    static class OrderService {
        int totalFor(String customer) {
            return customer.equals("acme") ? 42 : 0;
        }
    }

    static class ApplicationFactory implements CommandLine.IFactory {
        private final OrderService orderService;

        ApplicationFactory(OrderService orderService) {
            this.orderService = orderService;
        }

        @Override
        public <K> K create(Class<K> cls) throws Exception {
            if (cls == TotalCommand.class) {
                return cls.cast(new TotalCommand(orderService));
            }
            throw new IllegalArgumentException("Unexpected command type: " + cls.getName());
        }
    }
}
