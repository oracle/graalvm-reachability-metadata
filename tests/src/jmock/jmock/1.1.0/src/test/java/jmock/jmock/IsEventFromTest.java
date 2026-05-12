/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jmock.jmock;

import java.util.EventObject;

import org.jmock.core.constraint.IsEventFrom;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IsEventFromTest {
    @Test
    void defaultConstructorMatchesEventObjectFromExpectedSource() {
        Object source = new Object();
        IsEventFrom constraint = new IsEventFrom(source);

        boolean sameSourceResult = constraint.eval(new EventObject(source));
        boolean differentSourceResult = constraint.eval(new EventObject(new Object()));
        boolean nonEventResult = constraint.eval(source);

        assertThat(sameSourceResult).isTrue();
        assertThat(differentSourceResult).isFalse();
        assertThat(nonEventResult).isFalse();
        assertThat(constraint.describeTo(new StringBuffer()).toString())
                .contains(EventObject.class.getName())
                .contains(source.toString());
    }
}
