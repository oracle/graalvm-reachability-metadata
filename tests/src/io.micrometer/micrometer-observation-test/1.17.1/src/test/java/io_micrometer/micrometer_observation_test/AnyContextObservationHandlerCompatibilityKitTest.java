/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_observation_test;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.AnyContextObservationHandlerCompatibilityKit;

public class AnyContextObservationHandlerCompatibilityKitTest
        extends AnyContextObservationHandlerCompatibilityKit {
    private static final ObservationHandler<Observation.Context> HANDLER = new AcceptingObservationHandler();

    @Override
    public ObservationHandler<Observation.Context> handler() {
        return HANDLER;
    }

    private static final class AcceptingObservationHandler implements ObservationHandler<Observation.Context> {
        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }
}
