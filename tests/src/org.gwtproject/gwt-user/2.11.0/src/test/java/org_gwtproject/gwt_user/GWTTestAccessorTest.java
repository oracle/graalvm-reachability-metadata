/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gwt.junit.client.GWTTestCase;
import com.google.gwt.junit.client.impl.GWTTestAccessor;
import com.google.gwt.junit.client.impl.MissingTestPlaceHolder;

import org.junit.jupiter.api.Test;

public class GWTTestAccessorTest {
    @Test
    public void createsGwtTestCaseFromClassNameAndInvokesPublicMethod() throws Throwable {
        GWTTestAccessor accessor = new GWTTestAccessor();
        String className = MissingTestPlaceHolder.class.getName();

        GWTTestCase test = accessor.newInstance(className);

        assertThat(test).isInstanceOf(MissingTestPlaceHolder.class);
        assertThat(accessor.invoke(test, className, "getName")).isNull();
    }

    @Test
    public void unwrapsInvocationTargetExceptionFromInvokedTestMethod() throws Throwable {
        GWTTestAccessor accessor = new GWTTestAccessor();
        String className = MissingTestPlaceHolder.class.getName();
        GWTTestCase test = accessor.newInstance(className);

        assertThatThrownBy(() -> accessor.invoke(test, className, "getModuleName"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("unexpected call");
    }
}
