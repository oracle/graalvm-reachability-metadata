/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class DynamicAccessUtils {

    private static final String TRACK_DYNAMIC_ACCESS_PREFIX = "-H:TrackDynamicAccess=path=";

    private static final String PRESERVE_PREFIX = "-H:Preserve=path=";

    public static List<String> buildArgsForClasspathEntries(List<File> classpathEntries) {
        List<File> sortedEntries = new ArrayList<>(classpathEntries);
        sortedEntries.sort(Comparator.comparing(File::getAbsolutePath));

        List<String> buildArgs = new ArrayList<>(sortedEntries.size() * 2);
        for (File classpathEntry : sortedEntries) {
            String path = classpathEntry.getAbsolutePath();
            buildArgs.add(TRACK_DYNAMIC_ACCESS_PREFIX + path);
            buildArgs.add(PRESERVE_PREFIX + path);
        }
        return buildArgs;
    }
}
