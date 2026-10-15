/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import java.util.List;

/**
 * Writes each selected real-use project's `user-code-filter.json` from the JARs of the target and
 * of the libraries its `build.gradle` declares (§real-use-tests/AR-real-use-test-harness.2).
 */
@SuppressWarnings("unused")
public abstract class GenerateRealUseFilterInvocationTask extends RealUseExecTask {

    @Override
    public List<String> commandFor(String coordinates) {
        return List.of(gradlew(), "generateRealUseFilter");
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "The declared libraries of the real-use project for " + coordinates + " do not resolve";
    }
}
