/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.tuple.MarshalledTupleEntry;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleMarshalledBinding;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.DatabaseEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link TupleMarshalledBinding} object creation and tuple marshalling.
 */
public class TupleMarshalledBindingTest {

    @Test
    void marshalsAndUnmarshalsEntryUsingPublicNoArgumentConstructor() {
        TupleMarshalledBinding binding = new TupleMarshalledBinding(MarshalledRecord.class);
        DatabaseEntry entry = new DatabaseEntry();

        binding.objectToEntry(new MarshalledRecord("alpha", 37), entry);
        Object result = binding.entryToObject(entry);

        assertThat(entry.getSize()).isGreaterThan(0);
        assertThat(result).isInstanceOf(MarshalledRecord.class);
        MarshalledRecord record = (MarshalledRecord) result;
        assertThat(record.name).isEqualTo("alpha");
        assertThat(record.score).isEqualTo(37);
    }

    public static final class MarshalledRecord implements MarshalledTupleEntry {
        private String name;
        private int score;

        public MarshalledRecord() {
        }

        private MarshalledRecord(String name, int score) {
            this.name = name;
            this.score = score;
        }

        @Override
        public void marshalEntry(TupleOutput dataOutput) {
            dataOutput.writeString(name);
            dataOutput.writeInt(score);
        }

        @Override
        public void unmarshalEntry(TupleInput dataInput) {
            name = dataInput.readString();
            score = dataInput.readInt();
        }
    }
}
