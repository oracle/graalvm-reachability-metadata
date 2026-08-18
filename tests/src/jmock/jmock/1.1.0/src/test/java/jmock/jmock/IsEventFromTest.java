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
    void defaultConstructorMatchesEventObjectWithSameSource() {
        Object source = new Object();
        IsEventFrom constraint = new IsEventFrom(source);

        assertThat(constraint.eval(new EventObject(source))).isTrue();
        assertThat(constraint.eval(new EventObject(new Object()))).isFalse();
        assertThat(constraint.eval("not an event")).isFalse();
    }

    @Test
    void describesExpectedEventTypeAndSource() {
        String source = "publisher";
        IsEventFrom constraint = new IsEventFrom(source);

        String description = constraint.describeTo(new java.lang.StringBuffer()).toString();

        assertThat(description)
                .contains(EventObject.class.getName())
                .contains(source);
    }
}
