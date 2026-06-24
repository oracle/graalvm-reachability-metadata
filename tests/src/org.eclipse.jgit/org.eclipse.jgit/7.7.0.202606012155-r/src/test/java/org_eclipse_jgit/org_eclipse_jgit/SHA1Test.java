/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jgit.org_eclipse_jgit;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.sha1.SHA1;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class SHA1Test {

    @Test
    void computesObjectIdUsingConfiguredSha1Implementation() {
        SHA1 sha1 = SHA1.newInstance();
        sha1.update("jgit".getBytes(StandardCharsets.UTF_8));

        ObjectId objectId = ObjectId.fromRaw(sha1.digest());

        assertThat(objectId.name()).isEqualTo("87790337b9a3c3ce7feb1b8393a726ac3a3126e3");
    }
}
