/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.glassfish.jersey.internal.util.ReflectionHelper;
import org.junit.jupiter.api.Test;

public class JerseyReflectionHelperAnonymous5Test {
    @Test
    void obtainsInheritedFieldsThroughPrivilegedAction() {
        assertThat(ReflectionHelper.getAllFieldsPA(ChildBean.class).run())
                .extracting(field -> field.getName())
                .contains("parent", "child");
    }

    private static class ParentBean {
        private String parent;
    }

    private static class ChildBean extends ParentBean {
        private String child;
    }
}
