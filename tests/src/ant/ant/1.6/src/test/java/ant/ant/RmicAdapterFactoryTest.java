/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.rmic.KaffeRmic;
import org.apache.tools.ant.taskdefs.rmic.RmicAdapter;
import org.apache.tools.ant.taskdefs.rmic.RmicAdapterFactory;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RmicAdapterFactoryTest {
    @Test
    void resolvesRmicAdapterFromFullyQualifiedClassName() {
        try {
            RmicAdapter adapter = RmicAdapterFactory.getRmic(
                    KaffeRmic.class.getName(),
                    new LoggingTask());

            assertThat(adapter).isInstanceOf(KaffeRmic.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class LoggingTask extends Task {
        @Override
        public void execute() {
        }
    }
}
