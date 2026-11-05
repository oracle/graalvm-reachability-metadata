/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_quartz_scheduler.quartz;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.quartz.CalendarIntervalScheduleBuilder;
import org.quartz.CalendarIntervalTrigger;
import org.quartz.DateBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.simpl.SimpleThreadPool;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Properties;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Quartz's calendar-interval trigger subsystem for monthly reminders.
 * A billing service schedules an end-of-month reminder and observes its next run in UTC.
 */
public class CalendarIntervalReminderTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    @Timeout(60)
    public void rollsMonthlyReminderFromLongMonthToFebruary() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "1");
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "monthly-reminder-scheduler");

        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        Date start = date(2025, 1, 31, 9, 0);
        JobDetail reminder = JobBuilder.newJob(ReminderJob.class)
                .withIdentity("invoice-reminder", "billing")
                .build();
        CalendarIntervalTrigger trigger = TriggerBuilder.<CalendarIntervalTrigger>newTrigger()
                .withIdentity("invoice-reminder-trigger", "billing")
                .forJob(reminder)
                .startAt(start)
                .withSchedule(CalendarIntervalScheduleBuilder.calendarIntervalSchedule()
                        .withInterval(1, DateBuilder.IntervalUnit.MONTH)
                        .inTimeZone(UTC)
                        .preserveHourOfDayAcrossDaylightSavings(true))
                .build();

        try {
            scheduler.scheduleJob(reminder, trigger);

            CalendarIntervalTrigger scheduledTrigger =
                    (CalendarIntervalTrigger) scheduler.getTrigger(trigger.getKey());
            assertTrue(scheduledTrigger.isPreserveHourOfDayAcrossDaylightSavings());
            assertEquals(DateBuilder.IntervalUnit.MONTH, scheduledTrigger.getRepeatIntervalUnit());
            assertEquals(1, scheduledTrigger.getRepeatInterval());
            assertEquals(start, scheduledTrigger.getStartTime());
            assertEquals(start, scheduledTrigger.getNextFireTime());
            assertEquals(date(2025, 2, 28, 9, 0), scheduledTrigger.getFireTimeAfter(start));
            assertEquals("UTC", scheduledTrigger.getTimeZone().getID());
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

    public static final class ReminderJob implements Job {

        @Override
        public void execute(JobExecutionContext context) {
            context.setResult("reminder-sent");
        }
    }
}
