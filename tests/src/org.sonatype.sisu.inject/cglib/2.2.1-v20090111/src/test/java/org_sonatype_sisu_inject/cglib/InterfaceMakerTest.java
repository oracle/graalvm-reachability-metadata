/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu_inject.cglib;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import net.sf.cglib.proxy.InterfaceMaker;
import org.junit.jupiter.api.Test;

public class InterfaceMakerTest {

    @Test
    void addsAllPublicMethodsFromAnInterface() {
        InterfaceMaker interfaceMaker = new InterfaceMaker();

        assertThatCode(() -> interfaceMaker.add(DescribedContract.class)).doesNotThrowAnyException();
    }

    public interface DescribedContract {
        String describe(String value) throws IOException;
    }
}
