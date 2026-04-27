/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ivy.ivy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LaunchTarget {
    private LaunchTarget() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Expected marker path and two payload arguments");
        }
        if (Thread.currentThread().getContextClassLoader() == null) {
            throw new IllegalStateException("Expected Ivy to install the launch class loader");
        }
        Files.write(Path.of(args[0]), List.of(args[1], args[2]), StandardCharsets.UTF_8);
    }
}
