/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongodb_driver_core;

import com.mongodb.WriteConcern;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class WriteConcernTest {
    @Test
    void valueOfFindsNamedWriteConcernsInitializedFromPublicConstants() {
        final WriteConcern majority = WriteConcern.valueOf("majority");
        final WriteConcern w1 = WriteConcern.valueOf("w1");
        final WriteConcern acknowledged = WriteConcern.valueOf("acknowledged");

        assertThat(majority).isSameAs(WriteConcern.MAJORITY);
        assertThat(majority.getWString()).isEqualTo("majority");
        assertThat(w1).isSameAs(WriteConcern.W1);
        assertThat(w1.getW()).isEqualTo(1);
        assertThat(acknowledged).isSameAs(WriteConcern.ACKNOWLEDGED);
        assertThat(acknowledged.isServerDefault()).isTrue();
    }

    @Test
    void writeConcernOptionsAreRenderedAsBsonDocument() {
        final WriteConcern writeConcern = WriteConcern.MAJORITY
                .withJournal(true)
                .withWTimeout(2, TimeUnit.SECONDS);

        assertThat(writeConcern.asDocument().toJson()).contains(
                "\"w\": \"majority\"",
                "\"wtimeout\": 2000",
                "\"j\": true");
        assertThat(writeConcern.isAcknowledged()).isTrue();
    }
}
