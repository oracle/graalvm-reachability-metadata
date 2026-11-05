/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_quartz_scheduler.quartz;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.calendar.HolidayCalendar;
import org.quartz.simpl.SimpleThreadPool;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Properties;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Quartz's calendar exclusion subsystem for a business-day invoice schedule.
 * An invoicing service registers a company holiday and verifies that its daily trigger skips it.
 */
public class HolidayCalendarScheduleTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    @Timeout(60)
    public void skipsCompanyHolidayWhenCalculatingNextInvoiceRun() throws Exception {
        Date companyHoliday = date(2025, 6, 16, 0, 0);
        HolidayCalendar holidays = new HolidayCalendar(UTC);
        holidays.addExcludedDate(companyHoliday);

        assertFalse(holidays.isTimeIncluded(companyHoliday.getTime()));
        assertTrue(holidays.isTimeIncluded(date(2025, 6, 17, 12, 0).getTime()));

        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "1");
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "invoice-calendar-scheduler");
        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();

        try {
            scheduler.addCalendar("company-holidays", holidays, false, false);
            JobDetail invoiceJob = JobBuilder.newJob(InvoiceJob.class)
                    .withIdentity("invoice-run", "billing")
                    .build();
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("invoice-trigger", "billing")
                    .forJob(invoiceJob)
                    .startAt(date(2025, 6, 15, 10, 0))
                    .modifiedByCalendar("company-holidays")
                    .withSchedule(CronScheduleBuilder.cronSchedule("0 0 9 * * ?").inTimeZone(UTC))
                    .build();

            scheduler.scheduleJob(invoiceJob, trigger);

            Trigger scheduledTrigger = scheduler.getTrigger(trigger.getKey());
            assertEquals(date(2025, 6, 17, 9, 0), scheduledTrigger.getNextFireTime());
            assertEquals("company-holidays", scheduledTrigger.getCalendarName());
        } finally {
            scheduler.shutdown(true);
        }
    }

    private static Date date(int year, int month, int day, int hour, int minute) {
        GregorianCalendar calendar = new GregorianCalendar(UTC);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, 0);
        return calendar.getTime();
    }

    public static final class InvoiceJob implements Job {

        @Override
        public void execute(JobExecutionContext context) {
            context.setResult("invoice-generated");
        }
    }
}
