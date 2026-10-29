/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_jcdi_2_0_spec;

import static org.assertj.core.api.Assertions.assertThat;

import javax.enterprise.event.Observes;
import javax.enterprise.event.Reception;
import javax.enterprise.event.TransactionPhase;
import javax.enterprise.util.AnnotationLiteral;
import org.junit.jupiter.api.Test;

public class AnnotationLiteralTest {
    @Test
    void comparesAnnotationMemberValues() {
        Observes first = new ObserverLiteral(Reception.IF_EXISTS, TransactionPhase.AFTER_SUCCESS);
        Observes same = new ObserverLiteral(Reception.IF_EXISTS, TransactionPhase.AFTER_SUCCESS);
        Observes differentReception = new ObserverLiteral(Reception.ALWAYS, TransactionPhase.AFTER_SUCCESS);
        Observes differentPhase = new ObserverLiteral(Reception.IF_EXISTS, TransactionPhase.BEFORE_COMPLETION);

        assertThat(first.annotationType()).isEqualTo(Observes.class);
        assertThat(first).isEqualTo(same).isNotEqualTo(differentReception).isNotEqualTo(differentPhase);
        assertThat(same).isEqualTo(first);
    }

    @Test
    void hashesAnnotationMemberNamesAndValues() {
        Observes literal = new ObserverLiteral(Reception.IF_EXISTS, TransactionPhase.AFTER_SUCCESS);
        int expected = ((127 * "notifyObserver".hashCode()) ^ Reception.IF_EXISTS.hashCode())
                + ((127 * "during".hashCode()) ^ TransactionPhase.AFTER_SUCCESS.hashCode());

        assertThat(literal.hashCode()).isEqualTo(expected);
        assertThat(literal.hashCode()).isEqualTo(expected);
        assertThat(literal).hasSameHashCodeAs(new ObserverLiteral(Reception.IF_EXISTS, TransactionPhase.AFTER_SUCCESS));
    }

    @Test
    void describesAnnotationMembers() {
        Observes literal = new ObserverLiteral(Reception.IF_EXISTS, TransactionPhase.AFTER_SUCCESS);

        String description = literal.toString();

        assertThat(description).startsWith("@" + Observes.class.getName() + "(")
                .contains("notifyObserver=IF_EXISTS", "during=AFTER_SUCCESS")
                .endsWith(")");
        assertThat(literal.toString()).isEqualTo(description);
    }

    private static final class ObserverLiteral extends AnnotationLiteral<Observes> implements Observes {
        private final Reception reception;
        private final TransactionPhase phase;

        private ObserverLiteral(Reception reception, TransactionPhase phase) {
            this.reception = reception;
            this.phase = phase;
        }

        @Override
        public Reception notifyObserver() {
            return reception;
        }

        @Override
        public TransactionPhase during() {
            return phase;
        }
    }
}
