package org.graalvm.internal.tck.harness.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.util.internal.VersionNumber

import java.util.regex.Matcher
import java.util.regex.Pattern

abstract class GroupUnsupportedLibraries extends DefaultTask {

    @Input
    @Option(option = "comments", description = "Provides github comments for library grouping as string.")
    abstract Property<String> getGithubComments()

    @TaskAction
    void action() {
        def pattern = Pattern.compile("--[\n ](.*?)[\n ]--")
        def matcher = pattern.matcher(getGithubComments().get())

        def libraryGroups = new HashMap<String, List<String>>()
        while (matcher.find()) {
            def coordinates = matcher.group(1)
            def coordinatesPart = coordinates.split(":")
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
