/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongo_java_driver;

import com.mongodb.BasicDBObject;
import com.mongodb.DefaultDBCallback;
import org.bson.BSONObject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class DBCollectionObjectFactoryTest {
    @Test
    void defaultCallbackCreatesBasicDBObjectInstances() {
        final DefaultDBCallback callback = new DefaultDBCallback(null);

        final BSONObject document = callback.create();

        document.put("name", "mongo");
        assertThat(document).isInstanceOf(BasicDBObject.class);
        assertThat(document.get("name")).isEqualTo("mongo");
    }

    @Test
    void defaultCallbackCreatesBasicDBObjectForNestedDocumentPath() {
        final DefaultDBCallback callback = new DefaultDBCallback(null);

        final BSONObject document = callback.create(false, Arrays.asList("outer", "inner"));

        document.put("nested", true);
        assertThat(document).isInstanceOf(BasicDBObject.class);
        assertThat(document.get("nested")).isEqualTo(true);
    }
}
