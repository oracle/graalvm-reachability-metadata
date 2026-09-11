/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.asm.Label;
import com.sleepycat.persist.raw.RawField;
import com.sleepycat.persist.raw.RawObject;
import com.sleepycat.persist.raw.RawType;
import com.sleepycat.util.FastOutputStream;
import com.sleepycat.je.utilint.EventTrace;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MiscApiCoverageTest {

    @Test
    void rawObjectsLabelsAndFastStreamsRepresentValues() throws Exception {
        RawType type = new RawType() {
            @Override
            public String getClassName() {
                return "example.Raw";
            }

            @Override
            public int getVersion() {
                return 1;
            }

            @Override
            public boolean isSimple() {
                return true;
            }

            @Override
            public boolean isPrimitive() {
                return false;
            }

            @Override
            public boolean isEnum() {
                return false;
            }

            @Override
            public List<String> getEnumConstants() {
                return Collections.emptyList();
            }

            @Override
            public boolean isArray() {
                return false;
            }

            @Override
            public int getDimensions() {
                return 0;
            }

            @Override
            public RawType getComponentType() {
                return null;
            }

            @Override
            public Map<String, RawField> getFields() {
                return Collections.emptyMap();
            }

            @Override
            public RawType getSuperType() {
                return null;
            }
        };
        RawObject object = new RawObject(type, new Object[] {"value", 4});
        assertThat(object.getType()).isSameAs(type);
        assertThat(object.getElements()).containsExactly("value", 4);
        assertThat(object.hashCode()).isNotZero();
        assertThat(object.getValues()).isNull();
        assertThat(new RawObject(type, "READY").getEnum()).isEqualTo("READY");
        RawObject parent = new RawObject(type, Collections.singletonMap("name", "parent"), null);
        RawObject complex = new RawObject(type, Collections.singletonMap("name", "value"), parent);
        RawObject sameComplex = new RawObject(type,
                Collections.singletonMap("name", "value"), parent);
        assertThat(complex.getValues()).containsEntry("name", "value");
        assertThat(complex.getSuper()).isSameAs(parent);
        assertThat(complex).isEqualTo(sameComplex);
        assertThat(complex.toString()).contains("value", "parent");

        com.sleepycat.je.DatabaseEntry entry = new com.sleepycat.je.DatabaseEntry(
                new byte[] {1, 2, 3});
        assertThat(entry.hashCode()).isNotZero();
        assertThat(entry.toString()).contains("1");
        assertThat(com.sleepycat.je.JEVersion.CURRENT_VERSION.getMajor()).isPositive();
        assertThat(com.sleepycat.je.JEVersion.CURRENT_VERSION.getMinor()).isGreaterThanOrEqualTo(0);
        assertThat(com.sleepycat.je.JEVersion.CURRENT_VERSION.getPatch()).isGreaterThanOrEqualTo(0);
        assertThat(com.sleepycat.je.JEVersion.CURRENT_VERSION.getNumericVersionString())
                .isNotEmpty();
        assertThat(com.sleepycat.je.LockMode.DEFAULT.toString()).isNotEmpty();
        com.sleepycat.je.LogScanConfig scanConfig = new com.sleepycat.je.LogScanConfig();
        org.assertj.core.api.Assertions.assertThatThrownBy(scanConfig::cloneConfig)
                .isInstanceOf(ClassCastException.class);
        com.sleepycat.je.VerifyConfig verifyConfig = new com.sleepycat.je.VerifyConfig();
        verifyConfig.setShowProgressStream(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
        assertThat(verifyConfig.toString()).isNotEmpty();

        Label label = new Label();
        org.assertj.core.api.Assertions.assertThatThrownBy(label::getOffset)
                .isInstanceOf(IllegalStateException.class);
        assertThat(label.toString()).contains("L");
        FastOutputStream stream = new FastOutputStream();
        stream.write(new byte[] {1, 2, 3});
        assertThat(stream.toByteArray()).containsExactly(1, 2, 3);
        assertThat(stream.size()).isEqualTo(3);
        stream.reset();
        assertThat(stream.size()).isZero();
        EventTrace.addEvent(new EventTrace.ExceptionEventTrace());
        assertThat(new EventTrace.ExceptionEventTrace().toString()).isNotNull();
    }
}
