/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.runtime.Resources;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourcesAnonymous1Test {
    @Test
    void createsMessageAndPropertyInstances() {
        MessageResources messages = Resources.create(MessageResources.class);
        PropertyResources properties = Resources.create(new Properties(), PropertyResources.class);

        assertThat(messages.welcome("Ada").str()).isEqualTo("Welcome Ada");
        assertThat(properties.color().get()).isEqualTo("blue");
    }

    public interface MessageResources {
        @Resources.BaseMessage("Welcome {0}")
        Resources.Inst welcome(String name);
    }

    public interface PropertyResources {
        @Resources.Default("blue")
        Resources.StringProp color();
    }
}
