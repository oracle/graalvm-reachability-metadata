/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import com.mysql.cj.Messages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessagesTest {
    @Test
    void localizedMessagesLoadAndSubstituteConfigurationNames() {
        assertThat(Messages.getString("Connection.2")).isEqualTo("No operations allowed after connection closed.");
        assertThat(Messages.getString("ConnectionString.10", new Object[]{"applicationProfile"}))
                .isEqualTo("Can't find configuration template named 'applicationProfile'");
    }
}
