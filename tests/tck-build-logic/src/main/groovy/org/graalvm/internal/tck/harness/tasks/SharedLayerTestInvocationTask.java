/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Task that runs native tests with the shared Native Image base layer.
 */
@SuppressWarnings("unused")
public abstract class SharedLayerTestInvocationTask extends TestInvocationTask {
    private static final String CONTINUE_ON_COORDINATE_FAILURE_PROPERTY = "tck.layered.continueOnCoordinateFailure";
    private static final String COORDINATE_FAILURE_REPORT_PROPERTY = "tck.layered.coordinateFailureReport";

    @Override
    public List<String> commandFor(String coordinates) {
        List<String> command = super.commandFor(coordinates);
        appendBaseLayerFileProperty(command);
        return command;
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Shared layer test for " + coordinates + " failed with exit code " + exitCode + ".";
    }

    @Override
    protected boolean continueOnCoordinateFailure() {
        Object continueOnFailure = getProject().findProperty(CONTINUE_ON_COORDINATE_FAILURE_PROPERTY);
        return Boolean.parseBoolean(Objects.toString(continueOnFailure, "false"));
    }

    @Override
    protected File coordinateFailureReportFile() {
        Object reportPath = getProject().findProperty(COORDINATE_FAILURE_REPORT_PROPERTY);
        if (reportPath == null) {
            return null;
        }
        return getProject().file(reportPath.toString());
    }
}
