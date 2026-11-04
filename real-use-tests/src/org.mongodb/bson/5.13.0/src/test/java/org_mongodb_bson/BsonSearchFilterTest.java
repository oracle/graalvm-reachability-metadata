/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_bson;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.BsonRegularExpression;
import org.bson.BsonString;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises BSON regular-expression values for an application's search filter. */
class BsonSearchFilterTest {

    @Test
    @Timeout(60)
    void preservesAUserSearchFilterBeforeApplyingIt() {
        BsonDocument filter = new BsonDocument()
                .append("customerName", new BsonRegularExpression("^Acme", "i"))
                .append("region", new BsonString("west"));
        BsonDocumentCodec codec = new BsonDocumentCodec();

        BsonDocument received = roundTrip(codec, filter);
        BsonRegularExpression nameExpression = received.getRegularExpression("customerName");
        Pattern namePattern = compile(nameExpression);
        List<String> customerNames = List.of("Acme Supplies", "ACME Labs", "Globex");
        List<String> matchingNames = customerNames.stream()
                .filter(name -> namePattern.matcher(name).find())
                .toList();

        assertThat(nameExpression.getPattern()).isEqualTo("^Acme");
        assertThat(nameExpression.getOptions()).isEqualTo("i");
        assertThat(received.getString("region").getValue()).isEqualTo("west");
        assertThat(matchingNames).containsExactly("Acme Supplies", "ACME Labs");
    }

    private static BsonDocument roundTrip(BsonDocumentCodec codec, BsonDocument document) {
        BsonDocument wireDocument = new BsonDocument();
        BsonDocumentWriter writer = new BsonDocumentWriter(wireDocument);
        codec.encode(writer, document, EncoderContext.builder().build());
        writer.close();

        BsonDocumentReader reader = new BsonDocumentReader(wireDocument);
        BsonDocument decoded = codec.decode(reader, DecoderContext.builder().build());
        reader.close();
        return decoded;
    }

    private static Pattern compile(BsonRegularExpression expression) {
        int flags = expression.getOptions().contains("i") ? Pattern.CASE_INSENSITIVE : 0;
        return Pattern.compile(expression.getPattern(), flags);
    }
}
