/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_R4_core.vendor;

import java.util.Dictionary;
import org.osgi.framework.Bundle;
import org.osgi.service.condpermadmin.Condition;
import org.osgi.service.condpermadmin.ConditionInfo;

public final class BundleSignerCondition {
    private BundleSignerCondition() {
    }

    public static Condition getCondition(Bundle bundle, ConditionInfo info) {
        return new TestCondition(bundle, info);
    }

    private static final class TestCondition implements Condition {
        private final Bundle bundle;
        private final ConditionInfo info;

        private TestCondition(Bundle bundle, ConditionInfo info) {
            this.bundle = bundle;
            this.info = info;
        }

        public boolean isPostponed() {
            return false;
        }

        public boolean isSatisfied() {
            String[] args = info.getArgs();
            return bundle == null && args.length == 1 && "CN=Test Signer".equals(args[0]);
        }

        public boolean isMutable() {
            return false;
        }

        public boolean isSatisfied(Condition[] conditions, Dictionary context) {
            for (int index = 0; index < conditions.length; index++) {
                if (!conditions[index].isSatisfied()) {
                    return false;
                }
            }
            return true;
        }
    }
}
