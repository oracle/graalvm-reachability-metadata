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
        String[] args = info.getArgs();
        return new MatchedSignerCondition(bundle != null && "CN=Example Signer".equals(args[0]));
    }

    private static final class MatchedSignerCondition implements Condition {
        private final boolean satisfied;

        private MatchedSignerCondition(boolean satisfied) {
            this.satisfied = satisfied;
        }

        @Override
        public boolean isPostponed() {
            return false;
        }

        @Override
        public boolean isSatisfied() {
            return satisfied;
        }

        @Override
        public boolean isMutable() {
            return false;
        }

        @Override
        public boolean isSatisfied(Condition[] conditions, Dictionary context) {
            for (int i = 0; i < conditions.length; i++) {
                if (!conditions[i].isSatisfied()) {
                    return false;
                }
            }
            return true;
        }
    }
}
