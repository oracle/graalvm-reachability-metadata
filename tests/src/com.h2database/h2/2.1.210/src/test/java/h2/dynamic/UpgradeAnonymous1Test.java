/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2.dynamic;

import org.h2.tools.Upgrade;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class UpgradeAnonymous1Test {
    @Test
    void inMemoryDatabaseUpgradeReturnsBeforeCreatingHistoricalDriverClassLoader() throws Exception {
        boolean upgraded = Upgrade.upgrade("jdbc:h2:mem:upgrade-anonymous", new Properties(), 200);

        assertThat(upgraded).isFalse();
    }
}
