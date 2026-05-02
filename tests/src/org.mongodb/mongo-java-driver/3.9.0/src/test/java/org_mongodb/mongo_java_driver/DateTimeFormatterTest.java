/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongo_java_driver;

import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class DateTimeFormatterTest {
    private static final String DATE_TIME_STRING = "2019-01-02T03:04:05.006Z";

    @Test
    void parsesExtendedJsonDateString() {
        final long expectedEpochMillis = Instant.parse(DATE_TIME_STRING).toEpochMilli();
        final BsonDocument document = BsonDocument.parse("{\"createdAt\": {\"$date\": \"" + DATE_TIME_STRING + "\"}}");

        assertThat(document.getDateTime("createdAt").getValue()).isEqualTo(expectedEpochMillis);
    }

    @Test
    void writesRelaxedExtendedJsonDateString() {
        final BsonDateTime dateTime = new BsonDateTime(Instant.parse(DATE_TIME_STRING).toEpochMilli());
        final BsonDocument document = new BsonDocument("createdAt", dateTime);
        final JsonWriterSettings settings = JsonWriterSettings.builder()
                .outputMode(JsonMode.RELAXED)
                .build();

        assertThat(document.toJson(settings)).contains("\"$date\" : \"" + DATE_TIME_STRING + "\"");
    }
}
