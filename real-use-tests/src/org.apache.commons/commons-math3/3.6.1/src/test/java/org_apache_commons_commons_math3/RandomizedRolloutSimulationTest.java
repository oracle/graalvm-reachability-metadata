/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_math3;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Random data generation simulates feature-rollout activation cohorts for an application.
public class RandomizedRolloutSimulationTest {
    @Test
    public void forecastsActivatedUsersWithAReproducibleSimulation() {
        RandomDataGenerator randomData = new RandomDataGenerator(new MersenneTwister(8675309L));
        int activatedUsers = 0;
        int[] cohortActivations = new int[8];

        for (int cohort = 0; cohort < cohortActivations.length; cohort++) {
            cohortActivations[cohort] = randomData.nextBinomial(20, 0.65);
            activatedUsers += cohortActivations[cohort];
        }

        assertEquals(111, activatedUsers);
        assertEquals(17, cohortActivations[3]);
        assertEquals(14, cohortActivations[7]);
    }
}
