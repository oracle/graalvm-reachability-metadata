/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongo_java_driver;

import com.mongodb.ReflectionDBObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionDBObjectInnerJavaWrapperTest {
    @Test
    void wrapperDiscoversAndInvokesReflectionDBObjectAccessors() {
        final ReflectionDBObject.JavaWrapper wrapper = ReflectionDBObject.getWrapper(ReflectionDBObject.class);
        final ReflectionDocument document = new ReflectionDocument();

        final Object setterResult = wrapper.set(document, "_id", "document-1");
        final Object value = wrapper.get(document, "_id");

        assertThat(wrapper.keySet()).contains("_id");
        assertThat(setterResult).isNull();
        assertThat(value).isEqualTo("document-1");
        assertThat(document.get_id()).isEqualTo("document-1");
    }

    private static final class ReflectionDocument extends ReflectionDBObject {
    }
}
