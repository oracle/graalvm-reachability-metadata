/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_booter;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.ObjectUtils;
import org.apache.maven.surefire.shade.org.apache.commons.lang3.text.StrTokenizer;
import org.junit.jupiter.api.Test;

public class ObjectUtilsTest {

    @Test
    public void cloneCopiesPrimitiveArray() {
        int[] numbers = new int[]{1, 2, 3};

        int[] cloned = ObjectUtils.clone(numbers);

        assertThat(cloned).isNotSameAs(numbers).containsExactly(1, 2, 3);
    }

    @Test
    public void cloneInvokesPublicCloneMethodOnCloneableObject() {
        StrTokenizer tokenizer = new StrTokenizer("alpha,beta", ',');

        StrTokenizer cloned = ObjectUtils.clone(tokenizer);

        assertThat(cloned).isNotSameAs(tokenizer);
        assertThat(cloned.getTokenArray()).containsExactly("alpha", "beta");
    }
}
