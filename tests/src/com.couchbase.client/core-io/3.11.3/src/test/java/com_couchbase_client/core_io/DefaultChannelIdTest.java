/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.netty.channel.DefaultChannelId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class DefaultChannelIdTest {
    @Test
    void exercisesPublicApi() throws Exception {
        DefaultChannelId first = DefaultChannelId.newInstance();
        DefaultChannelId second = DefaultChannelId.newInstance();
        assertNotEquals(first.asLongText(), second.asLongText());
        assertFalse(first.asShortText().isEmpty());
        DefaultChannelId restored = SerializationSupport.roundTrip(first);
        assertEquals(first, restored);
        assertEquals(first.asLongText(), restored.asLongText());
    }
}
