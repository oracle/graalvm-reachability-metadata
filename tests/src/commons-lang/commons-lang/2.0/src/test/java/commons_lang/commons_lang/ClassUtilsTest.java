/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_lang.commons_lang;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.ClassUtils;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {

    @Test
    public void convertClassNamesToClassesLoadsAvailableClassesAndKeepsMissingEntriesNull() {
        List classNames = Arrays.asList(
                String.class.getName(),
                "not.a.RealClass",
                null,
                ClassUtils.class.getName());

        List classes = ClassUtils.convertClassNamesToClasses(classNames);

        assertThat(classes).containsExactly(String.class, null, null, ClassUtils.class);
    }
}
