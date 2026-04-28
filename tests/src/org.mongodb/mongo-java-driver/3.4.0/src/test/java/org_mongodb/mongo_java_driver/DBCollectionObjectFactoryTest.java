/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongo_java_driver;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.DefaultDBDecoder;
import com.mongodb.DefaultDBEncoder;
import org.bson.io.BasicOutputBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DBCollectionObjectFactoryTest {
    @Test
    void defaultDecoderCreatesBasicDbObjectsWhenNoCollectionIsProvided() {
        DBObject source = new BasicDBObject("name", "Ada Lovelace")
                .append("metrics", new BasicDBObject("visits", 3));
        BasicOutputBuffer outputBuffer = new BasicOutputBuffer();
        DefaultDBEncoder.FACTORY.create().writeObject(outputBuffer, source);

        DBObject decoded = DefaultDBDecoder.FACTORY.create().decode(outputBuffer.toByteArray(), (DBCollection) null);

        assertThat(decoded).isInstanceOf(BasicDBObject.class);
        assertThat(decoded.get("name")).isEqualTo("Ada Lovelace");
        assertThat(decoded.get("metrics")).isInstanceOf(BasicDBObject.class);
        DBObject metrics = (DBObject) decoded.get("metrics");
        assertThat(metrics.get("visits")).isEqualTo(3);
    }
}
