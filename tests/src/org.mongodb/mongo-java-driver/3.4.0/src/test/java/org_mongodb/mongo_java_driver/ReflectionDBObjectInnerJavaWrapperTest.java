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
    void mapsPublicBeanPropertiesThroughReflectionDBObjectApi() {
        DriverProfile document = new DriverProfile();

        assertThat(document.keySet()).contains("Name", "VisitCount");
        assertThat(document.containsField("Name")).isTrue();
        assertThat(document.containsField("VisitCount")).isTrue();

        assertThat(document.put("Name", "Ada Lovelace")).isNull();
        assertThat(document.put("VisitCount", 3)).isNull();

        assertThat(document.get("Name")).isEqualTo("Ada Lovelace");
        assertThat(document.get("VisitCount")).isEqualTo(3);
    }

    public static final class DriverProfile extends ReflectionDBObject {
        private String name;
        private Integer visitCount;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public Integer getVisitCount() {
            return visitCount;
        }

        public void setVisitCount(final Integer visitCount) {
            this.visitCount = visitCount;
        }
    }
}
