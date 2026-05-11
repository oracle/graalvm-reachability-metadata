/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.tree.LN;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@code LogEntryType} static log entry registration.
 */
public class LogEntryTypeTest {

    @Test
    void initializesRegisteredLogEntryTypes() {
        LogEntryType transactionalLnType = LogEntryType.LOG_LN_TRANSACTIONAL;

        assertThat(transactionalLnType.toString()).isEqualTo("LN_TX/0");
        assertThat(transactionalLnType.toStringNoVersion()).isEqualTo("LN_TX");
        assertThat(transactionalLnType.isNodeType()).isTrue();
        assertThat(transactionalLnType.isTransactional()).isTrue();
        assertThat(transactionalLnType.marshallOutsideLatch()).isTrue();
        assertThat(transactionalLnType.isTypeReplicated()).isTrue();
        assertThat(transactionalLnType.getSharedLogEntry()).isNotNull();

        Set<?> allTypes = LogEntryType.getAllTypes();
        assertThat(allTypes).hasSize(27).doesNotContainNull();
        assertThat(LogEntryType.findType((byte) 1, (byte) 0)).isSameAs(transactionalLnType);
    }

    @Test
    void legacyClassLiteralHelperLoadsLnClassByName() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(LogEntryType.class, MethodHandles.lookup());
        MethodHandle classLiteralHelper = lookup.findStatic(
                LogEntryType.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> lnClass = (Class<?>) classLiteralHelper.invokeExact(LN.class.getName());

        assertThat(lnClass).isSameAs(LN.class);
    }
}
