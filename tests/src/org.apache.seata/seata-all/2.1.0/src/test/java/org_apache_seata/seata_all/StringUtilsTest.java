/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import org.apache.seata.common.util.StringUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StringUtilsTest {
    @Test
    void toStringRendersPrivateAndInheritedFieldsOfUserTypes() {
        String rendered = StringUtils.toString(new SelfReferencingValue());

        assertThat(rendered)
                .startsWith("SelfReferencingValue(")
                .contains("name=\"seata\"")
                .contains("self=(this SelfReferencingValue)")
                .contains("count=7")
                .endsWith(")");
    }

    private static class ParentValue {
        private final int count = 7;
    }

    private static final class SelfReferencingValue extends ParentValue {
        private final String name = "seata";
        private final SelfReferencingValue self = this;
    }
}
