/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jmock.jmock;

import org.jmock.core.Verifiable;
import org.jmock.util.Verifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VerifierTest {
    @Test
    void verifyObjectVerifiesPrivateVerifiableFieldsDeclaredInClassHierarchy() {
        VerifierSubject subject = new VerifierSubject();

        Verifier.verifyObject(subject);

        assertThat(subject.baseVerifiable().verifiedCount()).isEqualTo(1);
        assertThat(subject.childVerifiable().verifiedCount()).isEqualTo(1);
    }
}

class VerifierBaseSubject {
    private final RecordingVerifiable baseVerifiable = new RecordingVerifiable();
    private final Object ignoredNonVerifiable = new Object();

    RecordingVerifiable baseVerifiable() {
        return baseVerifiable;
    }

    Object ignoredNonVerifiable() {
        return ignoredNonVerifiable;
    }
}

class VerifierSubject extends VerifierBaseSubject {
    private final RecordingVerifiable childVerifiable = new RecordingVerifiable();
    private final String ignoredText = "not verifiable";

    RecordingVerifiable childVerifiable() {
        return childVerifiable;
    }

    String ignoredText() {
        return ignoredText;
    }
}

class RecordingVerifiable implements Verifiable {
    private int verifiedCount;

    public void verify() {
        verifiedCount++;
    }

    int verifiedCount() {
        return verifiedCount;
    }
}
