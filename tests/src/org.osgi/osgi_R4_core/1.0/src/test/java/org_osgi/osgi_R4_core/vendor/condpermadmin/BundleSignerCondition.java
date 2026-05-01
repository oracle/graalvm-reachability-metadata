/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_R4_core.vendor.condpermadmin;

import java.util.Dictionary;
import org.osgi.framework.Bundle;
import org.osgi.service.condpermadmin.Condition;
import org.osgi.service.condpermadmin.ConditionInfo;

public final class BundleSignerCondition {
    public static int getConditionCalls;
    public static Bundle lastBundle;
    public static String lastSignerPattern;

    private BundleSignerCondition() {}

    public static void reset() {
        getConditionCalls = 0;
        lastBundle = null;
        lastSignerPattern = null;
    }

    public static Condition getCondition(Bundle bundle, ConditionInfo info) {
        getConditionCalls++;
        lastBundle = bundle;
        lastSignerPattern = info.getArgs()[0];
        return new SignerCondition(lastSignerPattern.startsWith("CN=Trusted"));
    }

    private static final class SignerCondition implements Condition {
        private final boolean satisfied;

        private SignerCondition(boolean satisfied) {
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
            for (Condition condition : conditions) {
                if (!condition.isSatisfied()) {
                    return false;
                }
            }
            return true;
        }
    }
}
