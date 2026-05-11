/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_common.wildfly_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.wildfly.common.string.CompositeCharSequence;

public class CompositeCharSequenceTest {
    @Test
    void composesMultipleSequencesAndIndexesAcrossSegmentBoundaries() {
        CompositeCharSequence sequence = new CompositeCharSequence("Wild", "Fly", " ", "Common");

        assertThat(sequence).hasToString("WildFly Common");
        assertThat(sequence.length()).isEqualTo("WildFly Common".length());
        assertThat(sequence.charAt(0)).isEqualTo('W');
        assertThat(sequence.charAt(3)).isEqualTo('d');
        assertThat(sequence.charAt(4)).isEqualTo('F');
        assertThat(sequence.charAt(6)).isEqualTo('y');
        assertThat(sequence.charAt(7)).isEqualTo(' ');
        assertThat(sequence.charAt(sequence.length() - 1)).isEqualTo('n');
    }

    @Test
    void subSequenceCanSpanSeveralUnderlyingSequences() {
        CompositeCharSequence sequence = new CompositeCharSequence(List.of("abc", "DEF", "ghi", "JKL"));

        assertThat(sequence.subSequence(0, 3)).hasToString("abc");
        assertThat(sequence.subSequence(2, 7)).hasToString("cDEFg");
        assertThat(sequence.subSequence(4, 10)).hasToString("EFghiJ");
        assertThat(sequence.subSequence(6, 12)).hasToString("ghiJKL");
        assertThat(sequence.subSequence(5, 5)).hasToString("");
    }

    @Test
    void equalityAndHashCodeUseFlattenedCharacterContent() {
        CompositeCharSequence first = new CompositeCharSequence("alpha", "-", "beta");
        CompositeCharSequence second = new CompositeCharSequence(List.of("alpha-", "beta"));
        CompositeCharSequence different = new CompositeCharSequence("alpha", ":", "beta");

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first).isEqualTo("alpha-beta");
        assertThat(first).isNotEqualTo(different);
        assertThat(first).isNotEqualTo(new Object());
    }
}
