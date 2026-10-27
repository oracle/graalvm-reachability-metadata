/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.io.netty.channel.DefaultChannelId;
import org.junit.jupiter.api.Test;

public class DefaultChannelIdTest {
    @Test
    void createsStableShortAndLongChannelIdentifiers() {
        DefaultChannelId first = DefaultChannelId.newInstance();
        DefaultChannelId second = DefaultChannelId.newInstance();

        assertThat(first.asShortText()).hasSize(8);
        assertThat(first.asLongText()).contains("-");
        assertThat(second).isNotEqualTo(first);
    }
}
