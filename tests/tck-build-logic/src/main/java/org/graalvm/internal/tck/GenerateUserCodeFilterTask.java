/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import org.graalvm.internal.tck.utils.GeneralUtils;
import org.graalvm.internal.tck.utils.MetadataGenerationUtils;
import org.graalvm.internal.tck.utils.MetadataIndexUpdateUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/// Writes what `generateMetadata --agentAllowedPackages=fromJar` writes besides metadata, but
/// compiles and runs nothing, so a test project that does not build still gets its filter
/// (§forge/AR-forge-workflow-pipeline.4.1): `user-code-filter.json` from the entry's
/// `allowed-packages`, the resolved JAR's package roots, and the test-source packages; the agent
/// block, when `build.gradle` has none; and the JAR's roots added to the entry's
/// `allowed-packages`, which `generateMetadata` records only after the tests pass.
///
/// Implements §AR-test-harness.5 — the `generateUserCodeFilter` authoring helper.
public abstract class GenerateUserCodeFilterTask extends DefaultTask {

    private String coordinates;

    {
        Object coordinatesProperty = getProject().findProperty("coordinates");
        if (coordinatesProperty != null) {
            this.coordinates = coordinatesProperty.toString();
        }
    }

    @Inject
    protected abstract ProjectLayout getLayout();

    @Option(option = "coordinates", description = "Coordinates in the form of group:artifact:version")
    public void setCoordinates(String coordinates) {
        this.coordinates = coordinates;
    }

    @Input
    public String getCoordinates() {
        return coordinates;
    }

    @TaskAction
    public void run() throws IOException {
        Coordinates coordinatesValue = Coordinates.parse(coordinates);
        Path testsDirectory = GeneralUtils.computeTestsDirectory(getLayout(), coordinates);
        List<String> packages = MetadataGenerationUtils.allowedPackagesFromJar(getProject(), getLayout(), coordinatesValue);
        MetadataGenerationUtils.writeUserCodeFilter(testsDirectory, packages);
        MetadataIndexUpdateUtils.setAllowedPackagesInIndexJson(getLayout(), coordinatesValue, packages);
    }
}
