package org.graalvm.internal.tck.updaters

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.util.internal.VersionNumber

abstract class GroupUnsupportedLibraries extends DefaultTask {

    @Input
    @Option(option = "libraries", description = "Provides list of libraries that should be grouped.")
    abstract Property<String> getLibraries()

    @TaskAction
    void action() {
        def libraries = getLibraries().get().split("\n")

        Map<String, List<String>> libraryGroups = new HashMap<String, List<String>>()
        for (def library : libraries) {
            def coordinatesPart = library.split("_")
            def artifactKey = coordinatesPart[0] + ":" + coordinatesPart[1]
            def version = coordinatesPart[2]

            if (libraryGroups.get(artifactKey) == null) {
                libraryGroups.put(artifactKey, new ArrayList<String>())
            }

            libraryGroups.get(artifactKey).add(version)
            libraryGroups.get(artifactKey).sort(Comparator.comparing(VersionNumber::parse))
        }

        print generateGroupedComment(libraryGroups)
    }

    private static String generateGroupedComment(Map<String, List<String>> groups) {
        def sb = new StringBuilder("List of all unsupported libraries:\n")

        for (String library : groups.keySet()) {
            sb.append("- ").append(library).append(":\n")

            for (String version : groups.get(library)) {
                sb.append("\t").append("- ").append(version).append("\n")
            }

            sb.append("\n")
        }

        return sb.toString()
    }

}
