/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.hadoop.ipc.RemoteException;
import org.junit.jupiter.api.Test;

public class RemoteExceptionTest {
    @Test
    void unwrapRemoteExceptionInstantiatesNamedIOException() {
        RemoteException remoteException = new RemoteException(
                FileNotFoundException.class.getName(),
                "remote path is missing");

        IOException unwrappedException = remoteException.unwrapRemoteException();

        assertThat(unwrappedException).isInstanceOf(FileNotFoundException.class);
        assertThat(unwrappedException).hasMessage("remote path is missing");
        assertThat(unwrappedException).hasCause(remoteException);
    }
}
