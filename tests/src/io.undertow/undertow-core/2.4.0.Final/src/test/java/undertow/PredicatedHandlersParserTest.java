/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.PredicatedHandlersParser;
import io.undertow.util.Methods;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PredicatedHandlersParserTest {

    @Test
    void parsesSingleValueForArrayParameter() {
        Predicate predicate = PredicatedHandlersParser.parsePredicate("method(GET)", classLoader());
        HttpServerExchange exchange = new HttpServerExchange(null);

        exchange.setRequestMethod(Methods.GET);
        assertThat(predicate.resolve(exchange)).isTrue();

        exchange.setRequestMethod(Methods.POST);
        assertThat(predicate.resolve(exchange)).isFalse();
    }

    @Test
    void parsesExplicitArrayParameter() {
        Predicate predicate = PredicatedHandlersParser.parsePredicate("method({GET, POST})", classLoader());
        HttpServerExchange exchange = new HttpServerExchange(null);

        exchange.setRequestMethod(Methods.GET);
        assertThat(predicate.resolve(exchange)).isTrue();

        exchange.setRequestMethod(Methods.POST);
        assertThat(predicate.resolve(exchange)).isTrue();

        exchange.setRequestMethod(Methods.PUT);
        assertThat(predicate.resolve(exchange)).isFalse();
    }

    private static ClassLoader classLoader() {
        return PredicatedHandlersParserTest.class.getClassLoader();
    }
}
