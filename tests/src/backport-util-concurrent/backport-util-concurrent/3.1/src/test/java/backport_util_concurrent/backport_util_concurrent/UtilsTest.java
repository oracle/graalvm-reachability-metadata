/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.helpers.Utils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsTest {
    private static final String NANO_TIMER_PROVIDER_PROPERTY =
            "edu.emory.mathcs.backport.java.util.concurrent.NanoTimerProvider";

    private static String previousNanoTimerProvider;

    @BeforeAll
    static void configureCustomNanoTimerProvider() {
        previousNanoTimerProvider = System.getProperty(NANO_TIMER_PROVIDER_PROPERTY);
        System.setProperty(
                NANO_TIMER_PROVIDER_PROPERTY,
                edu.emory.mathcs.backport.java.util.ArrayList.class.getName());
    }

    @AfterAll
    static void restoreCustomNanoTimerProvider() {
        if (previousNanoTimerProvider == null) {
            System.clearProperty(NANO_TIMER_PROVIDER_PROPERTY);
        } else {
            System.setProperty(NANO_TIMER_PROVIDER_PROPERTY, previousNanoTimerProvider);
        }
    }

    @Test
    void nanoTimeFallsBackWhenConfiguredProviderIsNotANanoTimer() {
        long firstReading = Utils.nanoTime();
        long secondReading = Utils.nanoTime();

        assertThat(secondReading).isGreaterThanOrEqualTo(firstReading);
    }

    @Test
    void collectionToArrayTrimsWhenCollectionSizeIsOverreported() {
        Collection<String> collection = collectionWithReportedSize(Arrays.asList("first", "second"), 4);

        Object[] contents = Utils.collectionToArray(collection);

        assertThat(contents).containsExactly("first", "second");
        assertThat(contents).hasSize(2);
    }

    @Test
    void collectionToArrayCreatesTypedArrayWhenDestinationIsTooSmall() {
        Collection<String> collection = collectionWithReportedSize(Arrays.asList("red", "green", "blue"), 3);

        String[] contents = (String[]) Utils.collectionToArray(collection, new String[0]);

        assertThat(contents).containsExactly("red", "green", "blue");
        assertThat(contents).isInstanceOf(String[].class);
    }

    private static <E> Collection<E> collectionWithReportedSize(Collection<E> elements, int reportedSize) {
        return new AbstractCollection<E>() {
            @Override
            public Iterator<E> iterator() {
                return elements.iterator();
            }

            @Override
            public int size() {
                return reportedSize;
            }
        };
    }
}
