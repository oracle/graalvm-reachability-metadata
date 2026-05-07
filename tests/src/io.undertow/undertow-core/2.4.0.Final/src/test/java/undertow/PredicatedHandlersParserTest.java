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
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PredicatedHandlersParserTest {

    @Test
    public void parsesSingleArrayParameterValue() {
        Predicate predicate = PredicatedHandlersParser.parsePredicate("method(GET)", classLoader());

        assertThat(predicate.resolve(exchangeWithMethod(Methods.GET))).isTrue();
        assertThat(predicate.resolve(exchangeWithMethod(Methods.POST))).isFalse();
    }

    @Test
    public void parsesExplicitArrayParameterValue() {
        Predicate predicate = PredicatedHandlersParser.parsePredicate("method({GET, POST})", classLoader());

        assertThat(predicate.resolve(exchangeWithMethod(Methods.GET))).isTrue();
        assertThat(predicate.resolve(exchangeWithMethod(Methods.POST))).isTrue();
        assertThat(predicate.resolve(exchangeWithMethod(Methods.PUT))).isFalse();
    }

    private static ClassLoader classLoader() {
        return PredicatedHandlersParserTest.class.getClassLoader();
    }

    private static HttpServerExchange exchangeWithMethod(HttpString method) {
        return new HttpServerExchange(null).setRequestMethod(method);
    }
}
