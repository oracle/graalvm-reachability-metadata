/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.github.seregamorph.testsmartcontext;

import java.util.Map;

public final class SmartDirtiesTestsSupportState {

    private SmartDirtiesTestsSupportState() {
    }

    public static Object clearOrdering() {
        return SmartDirtiesTestsSupport.setEngineClassOrderStateMap(null);
    }

    @SuppressWarnings("unchecked")
    public static void restoreOrdering(Object ordering) {
        SmartDirtiesTestsSupport.setEngineClassOrderStateMap(
                (Map<String, Map<Class<?>, SmartDirtiesTestsSupport.ClassGroupState>>) ordering);
    }
}
