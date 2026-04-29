/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.apache.calcite.runtime.Resources;
import org.junit.jupiter.api.Test;

public class ResourcesAnonymous1Test {
    @Test
    void createsMessageInstanceFromResourceInterface() {
        TestMessages messages = Resources.create(TestMessages.class);

        Resources.Inst greeting = messages.greeting("Calcite");

        assertThat(greeting.str()).isEqualTo("Hello, Calcite");
    }

    @Test
    void createsPropertyInstanceFromPropertiesBackedInterface() {
        Properties properties = new Properties();
        properties.setProperty("PlannerName", "volcano");
        TestProperties testProperties = Resources.create(properties, TestProperties.class);

        Resources.StringProp plannerName = testProperties.plannerName();

        assertThat(plannerName.isSet()).isTrue();
        assertThat(plannerName.get()).isEqualTo("volcano");
    }

    private interface TestMessages {
        @Resources.BaseMessage("Hello, {0}")
        Resources.Inst greeting(String name);
    }

    private interface TestProperties {
        @Resources.Default("hep")
        Resources.StringProp plannerName();
    }
}
