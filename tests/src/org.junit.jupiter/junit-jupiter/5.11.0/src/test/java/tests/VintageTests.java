package tests;

import org.hamcrest.MatcherAssert;
import org.hamcrest.core.Every;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;

public class VintageTests {

    /* passes but with some exceptions ("as warning") */

    @Test
    public void testEvery() {
        List<Integer> numbers = Arrays.asList(1, 1, 1, 1);
        MatcherAssert.assertThat(numbers, Every.everyItem(is(1)));
    }

    @SuppressWarnings("deprecation")
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testExpectedException() {
        expectedException.expect(ArithmeticException.class);
        throw new ArithmeticException();
    }

    @Test
    public void testExpectedExceptionCause() {
        expectedException.expectCause(instanceOf(ArithmeticException.class));
        try {
            throw new ArithmeticException();
        } catch (ArithmeticException e) {
            throw new RuntimeException(e);
        }
    }

}
