/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.quarkus.runner.bootstrap;

import java.util.List;
import java.util.Set;

import io.quarkus.bootstrap.app.AugmentAction;
import io.quarkus.bootstrap.app.AugmentResult;
import io.quarkus.bootstrap.app.ClassChangeInformation;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.StartupAction;

public final class AugmentActionImpl implements AugmentAction {

    private final CuratedApplication application;
    private final List<Object> buildChainCustomizers;
    private final List<Object> buildExecutionCustomizers;
    private final List<Object> extraCustomizers;

    public AugmentActionImpl(CuratedApplication application) {
        this(application, List.of(), List.of(), List.of());
    }

    public AugmentActionImpl(CuratedApplication application, List<?> buildChainCustomizers,
            List<?> buildExecutionCustomizers) {
        this(application, buildChainCustomizers, buildExecutionCustomizers, List.of());
    }

    public AugmentActionImpl(CuratedApplication application, List<?> buildChainCustomizers,
            List<?> buildExecutionCustomizers, List<?> extraCustomizers) {
        this.application = application;
        this.buildChainCustomizers = copy(buildChainCustomizers);
        this.buildExecutionCustomizers = copy(buildExecutionCustomizers);
        this.extraCustomizers = copy(extraCustomizers);
    }

    public CuratedApplication getApplication() {
        return application;
    }

    public List<Object> getBuildChainCustomizers() {
        return buildChainCustomizers;
    }

    public List<Object> getBuildExecutionCustomizers() {
        return buildExecutionCustomizers;
    }

    public List<Object> getExtraCustomizers() {
        return extraCustomizers;
    }

    private static List<Object> copy(List<?> values) {
        return List.copyOf(values);
    }

    @Override
    public void performCustomBuild(String resultConsumer, Object context, String... finalOutputs) {
        throw new UnsupportedOperationException("Custom builds are not needed by this test fixture");
    }

    @Override
    public AugmentResult createProductionApplication() {
        throw new UnsupportedOperationException("Production applications are not needed by this test fixture");
    }

    @Override
    public StartupAction createInitialRuntimeApplication() {
        throw new UnsupportedOperationException("Runtime applications are not needed by this test fixture");
    }

    @Override
    public StartupAction reloadExistingApplication(boolean hasStartedSuccessfully, Set<String> changedResources,
            ClassChangeInformation classChangeInformation) {
        throw new UnsupportedOperationException("Reloads are not needed by this test fixture");
    }
}
