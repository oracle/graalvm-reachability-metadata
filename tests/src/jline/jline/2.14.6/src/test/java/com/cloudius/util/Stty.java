/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.cloudius.util;

public final class Stty {

    public static int constructorCalls;

    public static int jlineModeCalls;

    public static int resetCalls;

    public Stty() {
        constructorCalls++;
    }

    public void jlineMode() {
        jlineModeCalls++;
    }

    public void reset() {
        resetCalls++;
    }

    public static void resetState() {
        constructorCalls = 0;
        jlineModeCalls = 0;
        resetCalls = 0;
    }
}
