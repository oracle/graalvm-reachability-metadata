/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.alibaba.com.caucho.hessian.io.HessianInput;
import com.alibaba.com.caucho.hessian.io.HessianOutput;
import com.alibaba.com.caucho.hessian.io.HessianServiceException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

public class HessianInputTest {
    @Test
    void readsFaultDetailWithRemoteMessage() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        HessianOutput output = new HessianOutput(bytes);
        output.startReply();
        output.writeFault("ServiceException", "remote failure", "payload");
        output.completeReply();
        output.flush();

        HessianInput input = new HessianInput(new ByteArrayInputStream(bytes.toByteArray()));

        Throwable thrown = catchThrowable(() -> input.readReply(Object.class));

        assertThat(thrown).isInstanceOf(HessianServiceException.class).hasMessage("remote failure");
        assertThat(((HessianServiceException) thrown).getDetail()).isEqualTo("payload");
    }
}
