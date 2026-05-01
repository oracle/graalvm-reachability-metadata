/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_razorvine.pyrolite.objects;

import java.util.HashMap;
import java.util.Map;

import net.razorvine.pyro.PyroException;
import net.razorvine.pyro.serializer.PyroSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionConstructorTest {
    @Test
    void deserializesSerpentExceptionDictionaryIntoPyroException() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("_pyroTraceback", "remote traceback\n");

        Map<String, Object> exception = new HashMap<>();
        exception.put("__class__", "builtins.ValueError");
        exception.put("__exception__", true);
        exception.put("args", new Object[] {"boom"});
        exception.put("attributes", attributes);

        PyroSerializer serializer = PyroSerializer.getSerpentSerializer();
        Object value = serializer.deserializeData(serializer.serializeData(exception));

        assertThat(value).isInstanceOfSatisfying(PyroException.class, problem -> {
            assertThat(problem).hasMessage("[builtins.ValueError] boom");
            assertThat(problem.pythonExceptionType).isEqualTo("builtins.ValueError");
            assertThat(problem._pyroTraceback).isEqualTo("remote traceback\n");
        });
    }
}
