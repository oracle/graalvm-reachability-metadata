/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_math3;

import org.apache.commons.math3.ode.FirstOrderDifferentialEquations;
import org.apache.commons.math3.ode.FirstOrderIntegrator;
import org.apache.commons.math3.ode.nonstiff.DormandPrince54Integrator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Numerically integrates a cooling-system model with Commons Math's ordinary differential equation subsystem.
public class CoolingProfileIntegrationTest {
    @Test
    public void predictsTheTemperatureAtTheInspectionTime() {
        FirstOrderIntegrator integrator = new DormandPrince54Integrator(1.0e-8, 0.25, 1.0e-10, 1.0e-10);
        double[] initialState = {80.0};
        double[] stateAtInspection = new double[1];

        integrator.integrate(new CoolingSystem(20.0, 0.25), 0.0, initialState, 4.0, stateAtInspection);

        assertEquals(42.0727664710, stateAtInspection[0], 1.0e-8);
    }

    private static final class CoolingSystem implements FirstOrderDifferentialEquations {
        private final double ambientTemperature;

        private final double heatLossRate;

        private CoolingSystem(double ambientTemperature, double heatLossRate) {
            this.ambientTemperature = ambientTemperature;
            this.heatLossRate = heatLossRate;
        }

        @Override
        public int getDimension() {
            return 1;
        }

        @Override
        public void computeDerivatives(double time, double[] state, double[] derivatives) {
            derivatives[0] = -heatLossRate * (state[0] - ambientTemperature);
        }
    }
}
