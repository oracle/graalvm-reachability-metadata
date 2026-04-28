/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_awaitility.awaitility;

import java.util.concurrent.Callable;

import org.awaitility.reflect.WhiteboxImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.fieldIn;

public class WhiteboxImplTest {

    @Test
    void readsPrivateInstanceFieldByName() {
        WhiteboxSubject subject = new WhiteboxSubject("Awaitility", 4, "tested");

        String name = WhiteboxImpl.getInternalState(subject, "name");

        assertThat(name).isEqualTo("Awaitility");
    }

    @Test
    void readsPrivateInstanceFieldByTypeThroughFieldSupplier() throws Exception {
        WhiteboxSubject subject = new WhiteboxSubject("Awaitility", 4, "tested");

        Callable<Integer> releaseLine = fieldIn(subject).ofType(Integer.class);

        assertThat(releaseLine.call()).isEqualTo(4);
    }

    @Test
    void readsPrivateInstanceFieldByNameAndTypeThroughFieldSupplier() throws Exception {
        WhiteboxSubject subject = new WhiteboxSubject("Awaitility", 4, "tested");

        Callable<String> status = fieldIn(subject).ofType(String.class).andWithName("status");

        assertThat(status.call()).isEqualTo("tested");
    }
}

class WhiteboxSubject {

    private final String name;

    private final Integer releaseLine;

    private final String status;

    WhiteboxSubject(String name, Integer releaseLine, String status) {
        this.name = name;
        this.releaseLine = releaseLine;
        this.status = status;
    }
}
