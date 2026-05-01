/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongo_java_driver;

import com.mongodb.WriteConcern;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class WriteConcernTest {
    @Test
    void namedWriteConcernsAreDiscoveredCaseInsensitively() {
        assertThat(WriteConcern.valueOf("acknowledged")).isSameAs(WriteConcern.ACKNOWLEDGED);
        assertThat(WriteConcern.valueOf("W1")).isSameAs(WriteConcern.W1);
        assertThat(WriteConcern.valueOf("w2")).isSameAs(WriteConcern.W2);
        assertThat(WriteConcern.valueOf("w3")).isSameAs(WriteConcern.W3);
        assertThat(WriteConcern.valueOf("unacknowledged")).isSameAs(WriteConcern.UNACKNOWLEDGED);
        assertThat(WriteConcern.valueOf("majority")).isSameAs(WriteConcern.MAJORITY);
        assertThat(WriteConcern.valueOf("unknown")).isNull();
    }

    @Test
    void writeConcernOptionsProduceExpectedDocument() {
        final WriteConcern concern = WriteConcern.MAJORITY
                .withWTimeout(2, TimeUnit.SECONDS)
                .withJournal(true);

        final BsonDocument document = concern.asDocument();

        assertThat(concern.isAcknowledged()).isTrue();
        assertThat(concern.getWString()).isEqualTo("majority");
        assertThat(concern.getWTimeout(TimeUnit.MILLISECONDS)).isEqualTo(2000);
        assertThat(concern.getJournal()).isTrue();
        assertThat(document.getString("w")).isEqualTo(new BsonString("majority"));
        assertThat(document.getInt32("wtimeout").getValue()).isEqualTo(2000);
        assertThat(document.getBoolean("j").getValue()).isTrue();
    }
}
