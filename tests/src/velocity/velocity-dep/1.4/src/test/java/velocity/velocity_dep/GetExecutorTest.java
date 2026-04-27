/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.parser.node.GetExecutor;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;

import java.util.Hashtable;

import static org.assertj.core.api.Assertions.assertThat;

public class GetExecutorTest {
    @Test
    void executeInvokesResolvedGetMethod() throws Exception {
        Hashtable<String, String> values = new Hashtable<>();
        values.put("language", "velocity");
        GetExecutor executor = newGetExecutor(values.getClass(), "language");

        Object value = executor.execute(values);

        assertThat(value).isEqualTo("velocity");
    }

    @Test
    void oldExecuteInvokesResolvedGetMethod() throws Exception {
        Hashtable<String, String> values = new Hashtable<>();
        values.put("engine", "templates");
        GetExecutor executor = newGetExecutor(values.getClass(), "engine");

        Object value = executor.OLDexecute(values, null);

        assertThat(value).isEqualTo("templates");
    }

    private GetExecutor newGetExecutor(Class<?> targetClass, String key) throws Exception {
        RuntimeInstance runtime = new RuntimeInstance();
        Introspector introspector = new Introspector(runtime);
        return new GetExecutor(runtime, introspector, targetClass, key);
    }
}
