/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.compiler.api.directives;

/**
 * Compatibility shim for libraries that still reference the pre-module Graal compiler API package.
 */
public final class GraalDirectives {

    public static final double LIKELY_PROBABILITY = 0.75d;
    public static final double UNLIKELY_PROBABILITY = 0.25d;
    public static final double SLOWPATH_PROBABILITY = 0.01d;
    public static final double FASTPATH_PROBABILITY = 0.99d;

    private GraalDirectives() {
    }

    public static boolean injectBranchProbability(double probability, boolean condition) {
        return condition;
    }
}
