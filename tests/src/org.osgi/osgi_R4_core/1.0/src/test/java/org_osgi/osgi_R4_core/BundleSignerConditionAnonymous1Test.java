/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_R4_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.osgi.service.condpermadmin.BundleSignerCondition;
import org.osgi.service.condpermadmin.Condition;
import org.osgi.service.condpermadmin.ConditionInfo;

public class BundleSignerConditionAnonymous1Test {
    static {
        System.setProperty("org.osgi.vendor.condpermadmin", "org_osgi.osgi_R4_core.vendor");
    }

    @Test
    void getConditionLoadsVendorBundleSignerConditionAndFindsGetConditionMethod() {
        ConditionInfo info = new ConditionInfo(
                "org.osgi.service.condpermadmin.BundleSignerCondition",
                new String[] {"CN=Test Signer"});

        Condition condition = BundleSignerCondition.getCondition(null, info);

        assertThat(condition.isPostponed()).isFalse();
        assertThat(condition.isMutable()).isFalse();
        assertThat(condition.isSatisfied()).isTrue();
    }
}
