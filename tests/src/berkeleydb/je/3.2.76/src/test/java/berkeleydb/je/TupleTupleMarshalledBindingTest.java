/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.tuple.MarshalledTupleEntry;
import com.sleepycat.bind.tuple.MarshalledTupleKeyEntity;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.bind.tuple.TupleTupleMarshalledBinding;
import com.sleepycat.je.DatabaseEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link TupleTupleMarshalledBinding} entity instantiation and tuple marshalling.
 */
public class TupleTupleMarshalledBindingTest {

    @Test
    void marshalsKeyAndDataAndUnmarshalsEntityUsingPublicNoArgumentConstructor() {
        TupleTupleMarshalledBinding binding = new TupleTupleMarshalledBinding(MarshalledEntity.class);
        MarshalledEntity original = new MarshalledEntity("account-7", "alice", 42, "region-1");
        DatabaseEntry keyEntry = new DatabaseEntry();
        DatabaseEntry dataEntry = new DatabaseEntry();

        binding.objectToKey(original, keyEntry);
        binding.objectToData(original, dataEntry);
        Object result = binding.entryToObject(keyEntry, dataEntry);

        assertThat(keyEntry.getSize()).isGreaterThan(0);
        assertThat(dataEntry.getSize()).isGreaterThan(0);
        assertThat(result).isInstanceOf(MarshalledEntity.class);
        MarshalledEntity entity = (MarshalledEntity) result;
        assertThat(entity.id).isEqualTo("account-7");
        assertThat(entity.owner).isEqualTo("alice");
        assertThat(entity.balance).isEqualTo(42);
        assertThat(entity.region).isEqualTo("region-1");
    }

    public static final class MarshalledEntity implements MarshalledTupleEntry, MarshalledTupleKeyEntity {
        private String id;
        private String owner;
        private int balance;
        private String region;

        public MarshalledEntity() {
        }

        private MarshalledEntity(String id, String owner, int balance, String region) {
            this.id = id;
            this.owner = owner;
            this.balance = balance;
            this.region = region;
        }

        @Override
        public void marshalEntry(TupleOutput dataOutput) {
            dataOutput.writeString(owner);
            dataOutput.writeInt(balance);
            dataOutput.writeString(region);
        }

        @Override
        public void unmarshalEntry(TupleInput dataInput) {
            owner = dataInput.readString();
            balance = dataInput.readInt();
            region = dataInput.readString();
        }

        @Override
        public void marshalPrimaryKey(TupleOutput keyOutput) {
            keyOutput.writeString(id);
        }

        @Override
        public void unmarshalPrimaryKey(TupleInput keyInput) {
            id = keyInput.readString();
        }

        @Override
        public boolean marshalSecondaryKey(String keyName, TupleOutput keyOutput) {
            if ("region".equals(keyName)) {
                keyOutput.writeString(region);
                return true;
            }
            return false;
        }

        @Override
        public boolean nullifyForeignKey(String keyName) {
            if ("region".equals(keyName)) {
                region = null;
                return true;
            }
            return false;
        }
    }
}
