/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_jdo.jdo2_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;

import javax.jdo.JDONullIdentityException;
import javax.jdo.JDOUserException;
import javax.jdo.identity.ObjectIdentity;

import org.junit.jupiter.api.Test;

public class ObjectIdentityTest {
    @Test
    void reconstructsNumericIdentityFromItsStringForm() {
        BigInteger key = new BigInteger("123456789012345678901234567890");
        ObjectIdentity original = new ObjectIdentity(Account.class, key);

        ObjectIdentity restored = new ObjectIdentity(Account.class, original.toString());

        assertThat(restored.getKey()).isEqualTo(key);
        assertThat(restored.getKeyAsObject()).isEqualTo(key);
        assertThat(restored.getTargetClass()).isEqualTo(Account.class);
        assertThat(restored.getTargetClassName()).isEqualTo(Account.class.getName());
        assertThat(restored).isEqualTo(original).hasSameHashCodeAs(original);
        assertThat(restored).isNotEqualTo(new ObjectIdentity(Invoice.class, key));
        assertThat(restored).isNotEqualTo(new ObjectIdentity(Account.class, key.add(BigInteger.ONE)));
    }

    @Test
    void preservesColonsInsideStringKeys() {
        ObjectIdentity identity = new ObjectIdentity(Account.class, "java.lang.String:region:east:42");

        assertThat(identity.getKey()).isEqualTo("region:east:42");
        assertThat(identity.toString()).isEqualTo("java.lang.String:region:east:42");
        assertThat(new ObjectIdentity(Account.class, identity.toString())).isEqualTo(identity);
    }

    @Test
    void rejectsNullKeysAndStringsWithoutATypeDelimiter() {
        assertThatThrownBy(() -> new ObjectIdentity(Account.class, null))
                .isInstanceOf(JDONullIdentityException.class)
                .hasMessageContaining("null");
        assertThatThrownBy(() -> new ObjectIdentity(Account.class, "unqualified-key"))
                .isInstanceOf(JDOUserException.class)
                .hasMessageContaining("unqualified-key")
                .hasMessageContaining(":");
    }

    public static final class Account {
    }

    public static final class Invoice {
    }
}
