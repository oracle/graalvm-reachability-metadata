/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_math3;

import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Numerical optimization selects the fuel reserve for a delivery route at its lowest operating cost.
public class DeliveryRouteOptimizationTest {
    @Test
    public void selectsTheLowestCostReserveWithinOperationalLimits() {
        UnivariatePointValuePair optimum = new BrentOptimizer(1.0e-10, 1.0e-10).optimize(
                new MaxEval(1000),
                new UnivariateObjectiveFunction(this::operatingCost),
                GoalType.MINIMIZE,
                new SearchInterval(0.0, 8.0));

        assertEquals(3.25, optimum.getPoint(), 1.0e-8);
        assertEquals(18.75, optimum.getValue(), 1.0e-8);
    }

    private double operatingCost(double reserveHours) {
        return 18.75 + 2.0 * Math.pow(reserveHours - 3.25, 2.0);
    }
}
