/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_json;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.json.PropertySetting;

import static org.assertj.core.api.Assertions.assertThat;

public class InstanceCoercerTest {
    @Test
    public void shouldPopulateObjectUsingDeclaredFields() {
        Json json = new Json();

        FieldBackedEvent event = json.toType(
            """
            {
              "name": "download",
              "retryCount": 3,
              "baseLabel": "queued",
              "ignored": "transient value",
              "sharedLabel": "static value",
              "unknown": "skipped"
            }
            """,
            FieldBackedEvent.class,
            PropertySetting.BY_FIELD);

        assertThat(event.getName()).isEqualTo("download");
        assertThat(event.getRetryCount()).isEqualTo(3);
        assertThat(event.getBaseLabel()).isEqualTo("queued");
        assertThat(event.getIgnored()).isNull();
        assertThat(FieldBackedEvent.getSharedLabel()).isNull();
    }

    @Test
    public void shouldPopulateObjectUsingBeanSetters() {
        Json json = new Json();

        BeanBackedEvent event = json.toType(
            """
            {
              "name": "upload",
              "retryCount": 5,
              "unknown": "skipped"
            }
            """,
            BeanBackedEvent.class,
            PropertySetting.BY_NAME);

        assertThat(event.getName()).isEqualTo("upload");
        assertThat(event.getRetryCount()).isEqualTo(5);
    }

    public static class FieldBackedBase {
        private String baseLabel;

        public String getBaseLabel() {
            return baseLabel;
        }
    }

    public static class FieldBackedEvent extends FieldBackedBase {
        private static String sharedLabel;

        private String name;
        private int retryCount;
        private transient String ignored;

        public String getName() {
            return name;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public String getIgnored() {
            return ignored;
        }

        public static String getSharedLabel() {
            return sharedLabel;
        }
    }

    public static class BeanBackedEvent {
        private String name;
        private int retryCount;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }
    }
}
