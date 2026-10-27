/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_observation_test;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.ConcreteContextObservationHandlerCompatibilityKit;

public class ConcreteContextObservationHandlerCompatibilityKitTest
        extends ConcreteContextObservationHandlerCompatibilityKit<
                ConcreteContextObservationHandlerCompatibilityKitTest.RequestContext> {
    private static final ObservationHandler<RequestContext> HANDLER = new RequestContextObservationHandler();
    private static final RequestContext CONTEXT = new RequestContext();

    @Override
    public ObservationHandler<RequestContext> handler() {
        return HANDLER;
    }

    @Override
    public RequestContext context() {
        return CONTEXT;
    }

    static final class RequestContext extends Observation.Context {
    }

    private static final class RequestContextObservationHandler implements ObservationHandler<RequestContext> {
        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof RequestContext;
        }
    }
}
