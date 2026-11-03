/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_quartz_scheduler.quartz;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.quartz.DailyTimeIntervalScheduleBuilder;
import org.quartz.DailyTimeIntervalTrigger;
import org.quartz.DateBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.TimeOfDay;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.simpl.SimpleThreadPool;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Properties;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises Quartz's daily time-interval trigger subsystem for a weekday report.
 * A reporting service schedules three daily report windows and observes the weekend gap.
 */
public class DailyTimeIntervalReportTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    @Timeout(60)
    public void schedulesWeekdayReportWindowAndSkipsWeekend() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "1");
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "daily-report-scheduler");

        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        JobDetail report = JobBuilder.newJob(DailyReportJob.class)
                .withIdentity("daily-report", "reports")
                .build();
        DailyTimeIntervalTrigger trigger = TriggerBuilder.<DailyTimeIntervalTrigger>newTrigger()
                .withIdentity("daily-report-window", "reports")
                .forJob(report)
                .startAt(date(2025, 6, 16, 8, 0))
                .withSchedule(DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule()
                        .onMondayThroughFriday()
                        .startingDailyAt(new TimeOfDay(9, 0))
                        .endingDailyAt(new TimeOfDay(10, 0))
                        .withInterval(30, DateBuilder.IntervalUnit.MINUTE))
                .build();

        try {
            Date firstRun = scheduler.scheduleJob(report, trigger);
            DailyTimeIntervalTrigger scheduledTrigger =
                    (DailyTimeIntervalTrigger) scheduler.getTrigger(trigger.getKey());

            assertNotNull(scheduledTrigger);
            assertEquals(date(2025, 6, 16, 9, 0), firstRun);
            assertEquals(firstRun, scheduledTrigger.getNextFireTime());
            assertEquals(DateBuilder.IntervalUnit.MINUTE, scheduledTrigger.getRepeatIntervalUnit());
            assertEquals(30, scheduledTrigger.getRepeatInterval());
            assertEquals(new TimeOfDay(9, 0), scheduledTrigger.getStartTimeOfDay());
            assertEquals(new TimeOfDay(10, 0), scheduledTrigger.getEndTimeOfDay());
            assertEquals(DailyTimeIntervalScheduleBuilder.MONDAY_THROUGH_FRIDAY,
                    scheduledTrigger.getDaysOfWeek());
            assertEquals(date(2025, 6, 16, 9, 30),
                    scheduledTrigger.getFireTimeAfter(date(2025, 6, 16, 9, 0)));
            assertEquals(date(2025, 6, 20, 10, 0),
                    scheduledTrigger.getFireTimeAfter(date(2025, 6, 20, 9, 30)));
            assertEquals(date(2025, 6, 23, 9, 0),
                    scheduledTrigger.getFireTimeAfter(date(2025, 6, 20, 10, 0)));
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

    private static final class DailyReportJob implements Job {

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            // The application job has no side effect for this schedule-observation scenario.
        }
    }
}
