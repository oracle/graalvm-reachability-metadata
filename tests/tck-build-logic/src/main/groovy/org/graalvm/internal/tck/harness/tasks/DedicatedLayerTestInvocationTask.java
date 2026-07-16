/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.graalvm.internal.tck.utils.BaseLayerUtils;

import java.io.File;
import java.util.List;

/**
 * Runs native tests with a coordinate-specific base layer containing the tested library.
 *
 * Implements §TCK-test-harness.3 — the library LayerUse test lane.
 */
@SuppressWarnings("unused")
public abstract class DedicatedLayerTestInvocationTask extends SharedLayerTestInvocationTask {
    private static final String DELETE_BASE_LAYER_AFTER_TEST_PROPERTY =
            "tck.layered.deleteDedicatedLayerAfterTest";

    @Override
    public List<String> commandFor(String coordinates) {
        List<String> command = super.commandFor(coordinates);
        command.add("-Ptck.dedicatedLayer=true");
        return command;
    }

    @Override
    protected File baseLayerFileFor(String coordinates) {
        return BaseLayerUtils.resolveDedicatedLayerFile(getProject(), coordinates);
    }

    @Override
    protected void afterEach(String coordinates) {
        super.afterEach(coordinates);
        deleteBaseLayerIfRequested(coordinates);
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        String message = "Dedicated layer test for " + coordinates + " failed with exit code " + exitCode + ".";
        deleteBaseLayerIfRequested(coordinates);
        return message;
    }

    private void deleteBaseLayerIfRequested(String coordinates) {
        Object property = getProject().findProperty(DELETE_BASE_LAYER_AFTER_TEST_PROPERTY);
        if (Boolean.parseBoolean(String.valueOf(property))) {
            File baseLayerDirectory = baseLayerFileFor(coordinates).getParentFile();
            getProject().delete(baseLayerDirectory);
            getLogger().lifecycle("Deleted dedicated layer at {}", baseLayerDirectory);
        }
    }
}
