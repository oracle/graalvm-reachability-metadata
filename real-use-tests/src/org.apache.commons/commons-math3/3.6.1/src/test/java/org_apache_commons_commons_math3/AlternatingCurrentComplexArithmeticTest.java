/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_math3;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Complex arithmetic models the voltage, current, and apparent power of an AC load.
public class AlternatingCurrentComplexArithmeticTest {
    @Test
    public void calculatesLoadCurrentAndApparentPower() {
        Complex sourceVoltage = ComplexUtils.polar2Complex(100.0, Math.PI / 6.0);
        Complex loadImpedance = new Complex(3.0, 4.0);

        Complex loadCurrent = sourceVoltage.divide(loadImpedance);
        Complex apparentPower = sourceVoltage.multiply(loadCurrent.conjugate());

        assertEquals(100.0, sourceVoltage.abs(), 1.0e-10);
        assertEquals(20.0, loadCurrent.abs(), 1.0e-10);
        assertEquals(1200.0, apparentPower.getReal(), 1.0e-10);
        assertEquals(1600.0, apparentPower.getImaginary(), 1.0e-10);
    }
}
