/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import java.lang.module.ModuleFinder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// Shares coordinate build policy while retaining each layer's own harness arguments.
/// Implements §FS-tests.3.2; the dedicated-layer wiring is §AR-test-harness.3.
public final class LayeredBuildArgsUtils {
    private static final String JUNIT_FEATURE = "--features=org.graalvm.junit.platform.JUnitPlatformFeature";
    // Native Build Tools adds these to test binaries; JDK-only bases have no JUnit classes.
    private static final List<String> JUNIT_EXCLUDES = List.of(
            "--exclude-config", ".*.jar", "META-INF\\/native-image\\/org.junit.*.properties.*");
    private static final Set<String> PATH_OPTIONS = Set.of(
            "-cp", "-classpath", "--class-path", "-p", "--module-path", "--upgrade-module-path");
    private static final Set<String> SYSTEM_MODULES = ModuleFinder.ofSystem().findAll().stream()
            .map(module -> module.descriptor().name()).collect(Collectors.toUnmodifiableSet());

    private LayeredBuildArgsUtils() {
    }

    /// Reuses the test policy in its original order, replacing the contiguous harness
    /// argument contribution without deduplicating repeated library option scopes.
    public static List<String> dedicatedLayerArguments(List<String> testArguments,
                                                     List<String> testHarnessArguments,
                                                     List<String> baseHarnessArguments) {
        List<String> policy = libraryArguments(testArguments, testHarnessArguments);
        policy.addAll(baseHarnessArguments);
        return policy;
    }

    /// Policy used to select and build a coordinate-specific shared base. §FS-tests.3.3
    public static List<String> libraryArguments(List<String> testArguments, List<String> testHarnessArguments) {
        List<String> arguments = new ArrayList<>(testArguments);
        if (!testHarnessArguments.isEmpty()) {
            int index = Collections.indexOfSubList(arguments, testHarnessArguments);
            if (index < 0) {
                throw new IllegalArgumentException("Native test harness arguments are missing from the test binary");
            }
            arguments.subList(index, index + testHarnessArguments.size()).clear();
        }
        arguments.remove(JUNIT_FEATURE);
        int junitIndex = Collections.indexOfSubList(arguments, JUNIT_EXCLUDES);
        if (junitIndex >= 0) {
            arguments.subList(junitIndex, junitIndex + JUNIT_EXCLUDES.size()).clear();
        }
        return libraryPolicy(arguments);
    }

    /// The dedicated base includes JUnit, so it needs the same JUnit setup as the test binary.
    public static List<String> junitBuildArguments() {
        List<String> arguments = new ArrayList<>(List.of(JUNIT_FEATURE));
        arguments.addAll(JUNIT_EXCLUDES);
        return arguments;
    }

    /// Test fixture paths belong to the application. Retain JDK module requests while
    /// dropping roots that require those application-only module paths. §FS-tests.3.2
    private static List<String> libraryPolicy(List<String> arguments) {
        List<String> policy = new ArrayList<>();
        for (int index = 0; index < arguments.size(); index++) {
            String argument = arguments.get(index);
            int equals = argument.indexOf('=');
            String option = equals < 0 ? argument : argument.substring(0, equals);
            if (PATH_OPTIONS.contains(option)) {
                if (equals < 0) {
                    index++;
                }
            } else if (option.equals("--add-modules")) {
                String modules = equals < 0 ? arguments.get(++index) : argument.substring(equals + 1);
                List<String> retained = List.of(modules.split(",")).stream()
                        .filter(module -> SYSTEM_MODULES.contains(module)
                                || module.equals("ALL-DEFAULT") || module.equals("ALL-SYSTEM"))
                        .toList();
                if (!retained.isEmpty()) {
                    if (equals < 0) {
                        policy.add(option);
                        policy.add(String.join(",", retained));
                    } else {
                        policy.add(option + "=" + String.join(",", retained));
                    }
                }
            } else {
                policy.add(argument);
            }
        }
        return policy;
    }
}
