/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

import static org.junit.jupiter.api.Assertions.*;

public class ReflectionUtilsAnonymous2Test {
    @Test
    void copiesMutableFieldState() {
        TestBeans.Fields source = new TestBeans.Fields();
        source.label = "copied";
        source.count = 17;
        TestBeans.Fields target = new TestBeans.Fields();
        ReflectionUtils.shallowCopyFieldState(source, target);
        assertEquals("copied", target.label);
        assertEquals(17, target.count);
    }
}
