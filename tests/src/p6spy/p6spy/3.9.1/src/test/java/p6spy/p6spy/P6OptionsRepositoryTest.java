/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package p6spy.p6spy;

import static org.assertj.core.api.Assertions.assertThat;

import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.appender.P6Logger;
import com.p6spy.engine.spy.appender.StdoutLogger;
import com.p6spy.engine.spy.option.P6OptionsRepository;
import org.junit.jupiter.api.Test;

public class P6OptionsRepositoryTest {
    @Test
    void createsConfiguredLoggerImplementation() {
        P6OptionsRepository repository = new P6OptionsRepository();

        assertThat(repository.set(P6Logger.class, "logger", StdoutLogger.class.getName())).isTrue();
        repository.initCompleted();
        P6Logger logger = repository.get(P6Logger.class, "logger");

        assertThat(logger).isInstanceOf(StdoutLogger.class);
        assertThat(logger.isCategoryEnabled(Category.STATEMENT)).isTrue();
    }
}
