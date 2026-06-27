/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import org.apache.lucene.util.VirtualMethod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VirtualMethodTest {
    private static final VirtualMethod<BaseDescription> DESCRIPTION_METHOD =
            new VirtualMethod<>(BaseDescription.class, "describe");

    @Test
    void reportsOverrideDistanceAcrossInheritanceHierarchy() {
        assertThat(DESCRIPTION_METHOD.getImplementationDistance(BaseDescription.class)).isZero();
        assertThat(DESCRIPTION_METHOD.getImplementationDistance(DerivedDescription.class)).isEqualTo(1);
        assertThat(DESCRIPTION_METHOD.getImplementationDistance(FurtherDerivedDescription.class)).isEqualTo(1);
        assertThat(DESCRIPTION_METHOD.isOverriddenAsOf(FurtherDerivedDescription.class)).isTrue();
    }

    public static class BaseDescription {
        public String describe() {
            return "base";
        }
    }

    public static class DerivedDescription extends BaseDescription {
        @Override
        public String describe() {
            return "derived";
        }
    }

    public static class FurtherDerivedDescription extends DerivedDescription {
    }
}
