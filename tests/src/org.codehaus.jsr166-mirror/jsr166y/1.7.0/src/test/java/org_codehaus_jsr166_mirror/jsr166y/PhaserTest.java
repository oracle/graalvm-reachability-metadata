/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jsr166_mirror.jsr166y;

import jsr166y.Phaser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PhaserTest {
    @Test
    void advancesSingleRegisteredPartyAndTerminatesAfterDeregistration() {
        Phaser phaser = new Phaser(1);

        assertThat(phaser.getPhase()).isZero();
        assertThat(phaser.getRegisteredParties()).isEqualTo(1);
        assertThat(phaser.getUnarrivedParties()).isEqualTo(1);
        assertThat(phaser.getArrivedParties()).isZero();

        int advancedPhase = phaser.arriveAndAwaitAdvance();

        assertThat(advancedPhase).isEqualTo(1);
        assertThat(phaser.getPhase()).isEqualTo(1);
        assertThat(phaser.getRegisteredParties()).isEqualTo(1);
        assertThat(phaser.getUnarrivedParties()).isEqualTo(1);

        int deregisteredPhase = phaser.arriveAndDeregister();

        assertThat(deregisteredPhase).isEqualTo(1);
        assertThat(phaser.isTerminated()).isTrue();
        assertThat(phaser.getRegisteredParties()).isZero();
    }
}
