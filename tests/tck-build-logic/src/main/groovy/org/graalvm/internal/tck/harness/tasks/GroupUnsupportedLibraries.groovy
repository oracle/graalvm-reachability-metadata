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
    @Option(option = "comments", description = "Provides a string that contains github comments for library grouping.")
    abstract Property<String> getGithubComments()

    @TaskAction
    void action() {
        Pattern pattern = Pattern.compile("--[\n ](.*?)[\n ]--")
        Matcher matcher = pattern.matcher(getGithubComments().get())

        Map<String, List<String>> libraryGroups = new HashMap<String, List<String>>()
        while (matcher.find()) {
            String coordinates = matcher.group(1)
            String[] coordinatesPart = coordinates.split(":")
            String artifactKey = coordinatesPart[0] + ":" + coordinatesPart[1]
            String version = coordinatesPart[2]

            if (libraryGroups.get(artifactKey) == null) {
                libraryGroups.put(artifactKey, new ArrayList<String>())
            }

            libraryGroups.get(artifactKey).add(version)
            libraryGroups.get(artifactKey).sort(Comparator.comparing(VersionNumber::parse))
        }

        print generateGroupedComment(libraryGroups)
    }

    private static String generateGroupedComment(Map<String, List<String>> groups) {
        StringBuilder sb = new StringBuilder("List of all unsupported libraries:\n")

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
