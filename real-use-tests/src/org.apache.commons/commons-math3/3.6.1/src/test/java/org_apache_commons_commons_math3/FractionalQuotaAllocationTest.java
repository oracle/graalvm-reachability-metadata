/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_math3;

import org.apache.commons.math3.fraction.Fraction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exact rational arithmetic allocates an application's shared storage quota without rounding drift.
public class FractionalQuotaAllocationTest {
    @Test
    public void allocatesRemainingQuotaAcrossServices() {
        Fraction totalQuota = new Fraction(1000, 1);
        Fraction backupReservation = totalQuota.multiply(new Fraction(7, 20));
        Fraction analyticsReservation = totalQuota.multiply(new Fraction(1, 7));
        Fraction serviceQuota = totalQuota.subtract(backupReservation).subtract(analyticsReservation)
                .divide(new Fraction(3, 1));

        assertEquals(new Fraction(3550, 21), serviceQuota);
        assertEquals(169.04761904761904, serviceQuota.doubleValue(), 1.0e-14);
        assertEquals(3550, serviceQuota.getNumerator());
        assertEquals(21, serviceQuota.getDenominator());

        Fraction releasedBackup = backupReservation.subtract(new Fraction(125, 1));
        assertEquals(new Fraction(225, 1), releasedBackup);
        assertEquals(new Fraction(4425, 7), totalQuota.subtract(analyticsReservation).subtract(releasedBackup));
    }
}
