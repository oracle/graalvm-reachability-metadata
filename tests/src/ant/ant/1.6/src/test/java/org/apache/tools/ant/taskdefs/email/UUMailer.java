/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.tools.ant.taskdefs.email;

import org.apache.tools.ant.BuildException;

public class UUMailer extends Mailer {
    public UUMailer() {
    }

    @Override
    public void send() throws BuildException {
        throw new BuildException("The test UUMailer should not send email");
    }
}
