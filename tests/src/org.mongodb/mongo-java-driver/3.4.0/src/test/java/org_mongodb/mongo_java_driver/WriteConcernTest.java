/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongo_java_driver;

import com.mongodb.WriteConcern;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class WriteConcernTest {
    @Test
    void resolvesNamedWriteConcernsFromPublicConstants() {
        assertThat(WriteConcern.valueOf("acknowledged")).isSameAs(WriteConcern.ACKNOWLEDGED);
        assertThat(WriteConcern.valueOf("W1")).isSameAs(WriteConcern.W1);
        assertThat(WriteConcern.valueOf("majority")).isSameAs(WriteConcern.MAJORITY);
        assertThat(WriteConcern.valueOf("journaled")).isSameAs(WriteConcern.JOURNALED);
    }

    @Test
    void derivesWriteConcernOptionsIntoBsonDocument() {
        WriteConcern writeConcern = WriteConcern.ACKNOWLEDGED
                .withW("analytics")
                .withWTimeout(2, TimeUnit.SECONDS)
                .withJournal(Boolean.TRUE);

        BsonDocument document = writeConcern.asDocument();

        assertThat(writeConcern.getWString()).isEqualTo("analytics");
        assertThat(writeConcern.getWTimeout(TimeUnit.MILLISECONDS)).isEqualTo(2000);
        assertThat(writeConcern.getJournal()).isTrue();
        assertThat(writeConcern.isAcknowledged()).isTrue();
        assertThat(document.getString("w").getValue()).isEqualTo("analytics");
        assertThat(document.getInt32("wtimeout").getValue()).isEqualTo(2000);
        assertThat(document.getBoolean("j").getValue()).isTrue();
    }
}
