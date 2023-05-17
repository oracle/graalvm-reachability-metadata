/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package quartz;

import org.quartz.Job;
import org.quartz.JobExecutionContext;

public class SimpleJob implements Job {

    @Override
    public void execute(JobExecutionContext context) {
        System.out.println("SimpleJob running");
    }
}
