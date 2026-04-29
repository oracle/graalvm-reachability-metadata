/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_barchart_udt.barchart_udt_bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barchart.udt.CCC;
import com.barchart.udt.FactoryInterfaceUDT;
import com.barchart.udt.FactoryUDT;
import org.junit.jupiter.api.Test;

public class FactoryUDTTest {
    @Test
    void createsCongestionControlInstancesFromConfiguredClass() {
        final FactoryUDT<CCC> factory = new FactoryUDT<>(CCC.class);

        final CCC congestionControl = factory.create();

        assertThat(congestionControl).isNotNull().isInstanceOf(CCC.class);
    }

    @Test
    void clonedFactoryCreatesIndependentCongestionControlInstances() {
        final FactoryUDT<CCC> factory = new FactoryUDT<>(CCC.class);
        final FactoryInterfaceUDT clonedFactory = factory.cloneFactory();

        final CCC firstControl = factory.create();
        final CCC secondControl = clonedFactory.create();

        assertThat(secondControl).isNotNull().isInstanceOf(CCC.class).isNotSameAs(firstControl);
    }

    @Test
    void rejectsClassesThatAreNotCongestionControls() {
        assertThatThrownBy(() -> new FactoryUDT<>(String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CCC");
    }
}
