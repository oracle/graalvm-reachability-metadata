/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl_ognl;

import ognl.MemberAccess;
import ognl.Ognl;
import ognl.OgnlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Member;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises OGNL's member-access subsystem for a customer support profile viewer.
 *
 * The viewer evaluates configured expressions against a profile while its member-access policy
 * exposes display fields and prevents the profile's internal token from being read.
 */
public class SupportProfileMemberAccessTest {
    @Test
    @Timeout(60)
    void evaluatesOnlyProfileFieldsAllowedByTheViewerPolicy() throws OgnlException {
        SupportProfile profile = new SupportProfile("Mina Patel", "Payments", "token-7f4a");
        Map context = Ognl.createDefaultContext(profile, new SupportViewerMemberAccess());

        Object displayName = Ognl.getValue("displayName", context, profile);
        Object department = Ognl.getValue("department", context, profile);

        assertThat(displayName).isEqualTo("Mina Patel");
        assertThat(department).isEqualTo("Payments");
        assertThatThrownBy(() -> Ognl.getValue("internalToken", context, profile))
                .isInstanceOf(OgnlException.class);
    }

    public static final class SupportViewerMemberAccess implements MemberAccess {
        private static final Set<String> EXPOSED_PROPERTIES = Set.of("displayName", "department");

        @Override
        public Object setup(Map context, Object target, Member member, String propertyName) {
            return null;
        }

        @Override
        public void restore(
                Map context,
                Object target,
                Member member,
                String propertyName,
                Object state) {
            // The viewer only reads immutable profile data, so there is no access state to restore.
        }

        @Override
        public boolean isAccessible(
                Map context, Object target, Member member, String propertyName) {
            return EXPOSED_PROPERTIES.contains(propertyName);
        }
    }

    public static final class SupportProfile {
        private final String displayName;
        private final String department;
        private final String internalToken;

        private SupportProfile(String displayName, String department, String internalToken) {
            this.displayName = displayName;
            this.department = department;
            this.internalToken = internalToken;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDepartment() {
            return department;
        }

        public String getInternalToken() {
            return internalToken;
        }
    }
}
