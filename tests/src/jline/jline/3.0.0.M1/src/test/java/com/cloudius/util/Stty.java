/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.cloudius.util;

public class Stty {

    public static int jlineModeCalls;
    public static int resetCalls;

    public static void clear() {
        jlineModeCalls = 0;
        resetCalls = 0;
    }

    public void jlineMode() {
        jlineModeCalls++;
    }

    public void reset() {
        resetCalls++;
    }
}
