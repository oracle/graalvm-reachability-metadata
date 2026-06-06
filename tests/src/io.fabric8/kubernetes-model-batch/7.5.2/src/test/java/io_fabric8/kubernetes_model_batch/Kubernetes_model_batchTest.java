/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_batch;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobList;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobListBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobStatus;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobStatusBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import io.fabric8.kubernetes.api.model.batch.v1.JobList;
import io.fabric8.kubernetes.api.model.batch.v1.JobListBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatusBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobTemplateSpec;
import io.fabric8.kubernetes.api.model.batch.v1.JobTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.PodFailurePolicy;
import io.fabric8.kubernetes.api.model.batch.v1.PodFailurePolicyBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.PodFailurePolicyOnExitCodesRequirement;
import io.fabric8.kubernetes.api.model.batch.v1.PodFailurePolicyRule;
import io.fabric8.kubernetes.api.model.batch.v1.SuccessPolicy;
import io.fabric8.kubernetes.api.model.batch.v1.SuccessPolicyBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.UncountedTerminatedPods;
import org.junit.jupiter.api.Test;

public class Kubernetes_model_batchTest {
    private static final String BATCH_API_GROUP = "batch";
    private static final String BATCH_V1_API_VERSION = BATCH_API_GROUP + "/v1";
    private static final String BATCH_V1BETA1_API_VERSION = BATCH_API_GROUP + "/v1beta1";

    @Test
    void jobBuilderCreatesIndexedJobWithFailureAndSuccessPolicy() {
        Job job = new JobBuilder()
                .withNewMetadata()
                .withName("indexed-render")
                .withNamespace("ml")
                .addToLabels("workload", "batch")
                .addToAnnotations("description", "indexed job")
                .endMetadata()
                .withNewSpec()
                .withParallelism(3)
                .withCompletions(6)
                .withCompletionMode("Indexed")
                .withBackoffLimit(4)
                .withBackoffLimitPerIndex(1)
                .withMaxFailedIndexes(2)
                .withManagedBy("controller.example/jobs")
                .withManualSelector(true)
                .withPodReplacementPolicy("Failed")
                .withTtlSecondsAfterFinished(600)
                .withNewSelector()
                .addToMatchLabels("job-name", "indexed-render")
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels("job-name", "indexed-render")
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .addNewContainer()
                .withName("worker")
                .withImage("busybox:1.36")
                .withCommand("sh", "-c")
                .withArgs("echo ${JOB_COMPLETION_INDEX}")
                .endContainer()
                .endSpec()
                .endTemplate()
                .withNewPodFailurePolicy()
                .addNewRule()
                .withAction("FailJob")
                .withNewOnExitCodes()
                .withContainerName("worker")
                .withOperator("In")
                .withValues(42, 137)
                .endOnExitCodes()
                .endRule()
                .addNewRule()
                .withAction("Ignore")
                .addNewOnPodCondition()
                .withType("DisruptionTarget")
                .withStatus("True")
                .endOnPodCondition()
                .endRule()
                .endPodFailurePolicy()
                .withNewSuccessPolicy()
                .addNewRule()
                .withSucceededCount(2)
                .withSucceededIndexes("0,2-3")
                .endRule()
                .endSuccessPolicy()
                .endSpec()
                .build();

        Job edited = job.toBuilder()
                .editSpec()
                .withSuspend(true)
                .editSuccessPolicy()
                .editFirstRule()
                .withSucceededCount(3)
                .endRule()
                .endSuccessPolicy()
                .endSpec()
                .build();

        assertThat(job.getApiVersion()).isEqualTo(BATCH_V1_API_VERSION);
        assertThat(job.getKind()).isEqualTo("Job");
        assertThat(job.getMetadata().getNamespace()).isEqualTo("ml");
        assertThat(job.getMetadata().getLabels()).containsEntry("workload", "batch");
        assertThat(job.getSpec().getCompletionMode()).isEqualTo("Indexed");
        assertThat(job.getSpec().getParallelism()).isEqualTo(3);
        assertThat(job.getSpec().getCompletions()).isEqualTo(6);
        assertThat(job.getSpec().getBackoffLimitPerIndex()).isEqualTo(1);
        assertThat(job.getSpec().getMaxFailedIndexes()).isEqualTo(2);
        assertThat(job.getSpec().getManagedBy()).isEqualTo("controller.example/jobs");
        assertThat(job.getSpec().getManualSelector()).isTrue();
        assertThat(job.getSpec().getPodReplacementPolicy()).isEqualTo("Failed");
        assertThat(job.getSpec().getSelector().getMatchLabels()).containsEntry("job-name", "indexed-render");
        assertThat(job.getSpec().getTemplate().getSpec().getRestartPolicy()).isEqualTo("Never");
        assertThat(job.getSpec().getTemplate().getSpec().getContainers()).hasSize(1);
        assertThat(job.getSpec().getTemplate().getSpec().getContainers().get(0).getCommand())
                .containsExactly("sh", "-c");
        assertThat(job.getSpec().getTemplate().getSpec().getContainers().get(0).getArgs())
                .containsExactly("echo ${JOB_COMPLETION_INDEX}");

        PodFailurePolicy failurePolicy = job.getSpec().getPodFailurePolicy();
        assertThat(failurePolicy.getRules()).hasSize(2);
        assertThat(failurePolicy.getRules().get(0).getAction()).isEqualTo("FailJob");
        assertThat(failurePolicy.getRules().get(0).getOnExitCodes().getContainerName()).isEqualTo("worker");
        assertThat(failurePolicy.getRules().get(0).getOnExitCodes().getOperator()).isEqualTo("In");
        assertThat(failurePolicy.getRules().get(0).getOnExitCodes().getValues()).containsExactly(42, 137);
        assertThat(failurePolicy.getRules().get(1).getAction()).isEqualTo("Ignore");
        assertThat(failurePolicy.getRules().get(1).getOnPodConditions().get(0).getType())
                .isEqualTo("DisruptionTarget");

        SuccessPolicy successPolicy = job.getSpec().getSuccessPolicy();
        assertThat(successPolicy.getRules()).hasSize(1);
        assertThat(successPolicy.getRules().get(0).getSucceededIndexes()).isEqualTo("0,2-3");
        assertThat(successPolicy.getRules().get(0).getSucceededCount()).isEqualTo(2);
        assertThat(edited.getSpec().getSuspend()).isTrue();
        assertThat(edited.getSpec().getSuccessPolicy().getRules().get(0).getSucceededCount()).isEqualTo(3);
        assertThat(job.getSpec().getSuspend()).isNull();
        assertThat(job.getSpec().getSuccessPolicy().getRules().get(0).getSucceededCount()).isEqualTo(2);
    }

    @Test
    void jobStatusTracksConditionsUncountedPodsAndBuilderMutations() {
        JobStatus status = new JobStatusBuilder()
                .withStartTime("2026-01-01T00:00:00Z")
                .withCompletionTime("2026-01-01T00:05:00Z")
                .withActive(1)
                .withReady(1)
                .withSucceeded(2)
                .withFailed(1)
                .withTerminating(0)
                .withCompletedIndexes("0,2")
                .withFailedIndexes("1")
                .addNewCondition()
                .withType("Complete")
                .withStatus("False")
                .withReason("Running")
                .withMessage("still running")
                .withLastProbeTime("2026-01-01T00:03:00Z")
                .withLastTransitionTime("2026-01-01T00:01:00Z")
                .endCondition()
                .addNewCondition()
                .withType("FailureTarget")
                .withStatus("True")
                .withReason("BackoffLimitExceeded")
                .endCondition()
                .withNewUncountedTerminatedPods()
                .addToSucceeded("pod-a", "pod-c")
                .addToFailed("pod-b")
                .endUncountedTerminatedPods()
                .addToAdditionalProperties("controller", "batch-test")
                .build();

        JobStatus edited = new JobStatusBuilder(status)
                .editMatchingCondition(condition -> "Complete".equals(condition.getType()))
                .withStatus("True")
                .withReason("Completed")
                .endCondition()
                .editUncountedTerminatedPods()
                .setToSucceeded(1, "pod-d")
                .addToFailed("pod-e")
                .endUncountedTerminatedPods()
                .removeFromAdditionalProperties("controller")
                .build();

        assertThat(status.getConditions()).hasSize(2);
        assertThat(status.getConditions().get(0).getType()).isEqualTo("Complete");
        assertThat(status.getConditions().get(0).getStatus()).isEqualTo("False");
        assertThat(status.getConditions().get(1).getReason()).isEqualTo("BackoffLimitExceeded");
        assertThat(status.getCompletedIndexes()).isEqualTo("0,2");
        assertThat(status.getFailedIndexes()).isEqualTo("1");
        assertThat(status.getAdditionalProperties()).containsEntry("controller", "batch-test");

        UncountedTerminatedPods pods = status.getUncountedTerminatedPods();
        assertThat(pods.getSucceeded()).containsExactly("pod-a", "pod-c");
        assertThat(pods.getFailed()).containsExactly("pod-b");
        assertThat(edited.getConditions().get(0).getStatus()).isEqualTo("True");
        assertThat(edited.getConditions().get(0).getReason()).isEqualTo("Completed");
        assertThat(edited.getUncountedTerminatedPods().getSucceeded()).containsExactly("pod-a", "pod-d");
        assertThat(edited.getUncountedTerminatedPods().getFailed()).containsExactly("pod-b", "pod-e");
        assertThat(edited.getAdditionalProperties()).doesNotContainKey("controller");
    }

    @Test
    void cronJobBuilderCreatesScheduledJobTemplateAndStatus() {
        CronJob cronJob = new CronJobBuilder()
                .withNewMetadata()
                .withName("nightly-cleanup")
                .withNamespace("ops")
                .addToLabels("app", "cleanup")
                .endMetadata()
                .withNewSpec()
                .withSchedule("0 3 * * *")
                .withTimeZone("Etc/UTC")
                .withConcurrencyPolicy("Forbid")
                .withStartingDeadlineSeconds(120L)
                .withSuccessfulJobsHistoryLimit(3)
                .withFailedJobsHistoryLimit(1)
                .withSuspend(false)
                .withNewJobTemplate()
                .withNewMetadata()
                .addToLabels("cronjob", "nightly-cleanup")
                .endMetadata()
                .withNewSpec()
                .withBackoffLimit(2)
                .withTtlSecondsAfterFinished(300)
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels("task", "cleanup")
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("OnFailure")
                .addNewContainer()
                .withName("cleaner")
                .withImage("alpine:3.20")
                .withArgs("rm", "-rf", "/tmp/work")
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .endJobTemplate()
                .endSpec()
                .withNewStatus()
                .addNewActive()
                .withApiVersion(BATCH_V1_API_VERSION)
                .withKind("Job")
                .withNamespace("ops")
                .withName("nightly-cleanup-28650060")
                .withUid("job-uid")
                .endActive()
                .withLastScheduleTime("2026-01-01T03:00:00Z")
                .withLastSuccessfulTime("2026-01-01T03:01:00Z")
                .endStatus()
                .build();

        CronJob edited = cronJob.edit()
                .editSpec()
                .withSchedule("30 3 * * *")
                .editJobTemplate()
                .editSpec()
                .withParallelism(2)
                .endSpec()
                .endJobTemplate()
                .endSpec()
                .editStatus()
                .editFirstActive()
                .withName("nightly-cleanup-28650090")
                .endActive()
                .endStatus()
                .build();

        assertThat(cronJob.getApiVersion()).isEqualTo(BATCH_V1_API_VERSION);
        assertThat(cronJob.getKind()).isEqualTo("CronJob");
        assertThat(cronJob.getSpec().getSchedule()).isEqualTo("0 3 * * *");
        assertThat(cronJob.getSpec().getTimeZone()).isEqualTo("Etc/UTC");
        assertThat(cronJob.getSpec().getConcurrencyPolicy()).isEqualTo("Forbid");
        assertThat(cronJob.getSpec().getSuccessfulJobsHistoryLimit()).isEqualTo(3);
        assertThat(cronJob.getSpec().getJobTemplate().getMetadata().getLabels())
                .containsEntry("cronjob", "nightly-cleanup");
        assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec().getContainers().get(0)
                .getName()).isEqualTo("cleaner");
        assertThat(cronJob.getStatus().getActive()).hasSize(1);
        ObjectReference activeJob = cronJob.getStatus().getActive().get(0);
        assertThat(activeJob.getApiVersion()).isEqualTo(BATCH_V1_API_VERSION);
        assertThat(activeJob.getKind()).isEqualTo("Job");
        assertThat(activeJob.getName()).isEqualTo("nightly-cleanup-28650060");
        assertThat(cronJob.getStatus().getLastSuccessfulTime()).isEqualTo("2026-01-01T03:01:00Z");
        assertThat(edited.getSpec().getSchedule()).isEqualTo("30 3 * * *");
        assertThat(edited.getSpec().getJobTemplate().getSpec().getParallelism()).isEqualTo(2);
        assertThat(edited.getStatus().getActive().get(0).getName()).isEqualTo("nightly-cleanup-28650090");
    }

    @Test
    void cronJobStatusManagesMultipleActiveJobReferences() {
        CronJobStatus status = new CronJobStatusBuilder()
                .addToActive(activeJobReference("nightly-cleanup-28650060", "job-uid-1"))
                .addToActive(activeJobReference("nightly-cleanup-28649999", "job-uid-0"))
                .withLastScheduleTime("2026-01-01T03:00:00Z")
                .withLastSuccessfulTime("2026-01-01T02:01:00Z")
                .build();

        CronJobStatus updated = new CronJobStatusBuilder(status)
                .editMatchingActive(reference -> "nightly-cleanup-28650060".equals(reference.getName()))
                .withResourceVersion("2000")
                .endActive()
                .removeMatchingFromActive(reference -> "nightly-cleanup-28649999".equals(reference.getName()))
                .addNewActive()
                .withApiVersion(BATCH_V1_API_VERSION)
                .withKind("Job")
                .withNamespace("ops")
                .withName("nightly-cleanup-28650120")
                .withUid("job-uid-2")
                .withResourceVersion("2001")
                .endActive()
                .withLastScheduleTime("2026-01-02T03:00:00Z")
                .withLastSuccessfulTime("2026-01-02T03:01:00Z")
                .build();

        assertThat(status.getActive()).hasSize(2);
        assertThat(status.getActive()).extracting(ObjectReference::getName)
                .containsExactly("nightly-cleanup-28650060", "nightly-cleanup-28649999");
        assertThat(status.getActive().get(0).getResourceVersion()).isNull();
        assertThat(status.getLastSuccessfulTime()).isEqualTo("2026-01-01T02:01:00Z");

        assertThat(new CronJobStatusBuilder(status)
                .hasMatchingActive(reference -> "nightly-cleanup-28650060".equals(reference.getName()))).isTrue();
        ObjectReference retained = new CronJobStatusBuilder(updated)
                .buildMatchingActive(reference -> "nightly-cleanup-28650060".equals(reference.getName()));
        assertThat(retained.getUid()).isEqualTo("job-uid-1");
        assertThat(retained.getResourceVersion()).isEqualTo("2000");
        assertThat(updated.getActive()).extracting(ObjectReference::getName)
                .containsExactly("nightly-cleanup-28650060", "nightly-cleanup-28650120");
        assertThat(updated.getLastScheduleTime()).isEqualTo("2026-01-02T03:00:00Z");
        assertThat(updated.getLastSuccessfulTime()).isEqualTo("2026-01-02T03:01:00Z");
        assertThat(status.getActive()).extracting(ObjectReference::getName)
                .containsExactly("nightly-cleanup-28650060", "nightly-cleanup-28649999");
    }

    @Test
    void standaloneJobTemplateSpecCanBeReusedAndCopied() {
        JobTemplateSpec template = new JobTemplateSpecBuilder()
                .withNewMetadata()
                .withName("backup-template")
                .addToLabels("template", "backup")
                .addToAnnotations("owner", "batch-team")
                .endMetadata()
                .withNewSpec()
                .withActiveDeadlineSeconds(900L)
                .withBackoffLimit(2)
                .withCompletions(1)
                .withParallelism(1)
                .withTtlSecondsAfterFinished(120)
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels("job-template", "backup")
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("OnFailure")
                .addNewContainer()
                .withName("backup")
                .withImage("busybox:1.36")
                .withArgs("sh", "-c", "echo backup")
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .addToAdditionalProperties("template-source", "shared")
                .build();
        CronJob nightly = cronJobWithTemplate("nightly-backup", "0 1 * * *", template);
        CronJob weekly = cronJobWithTemplate("weekly-backup", "0 2 * * 0", template);

        CronJob tunedWeekly = weekly.toBuilder()
                .editSpec()
                .editJobTemplate()
                .editSpec()
                .withParallelism(2)
                .withActiveDeadlineSeconds(1800L)
                .endSpec()
                .endJobTemplate()
                .endSpec()
                .build();

        assertThat(template.getMetadata().getLabels()).containsEntry("template", "backup");
        assertThat(template.getAdditionalProperties()).containsEntry("template-source", "shared");
        assertThat(nightly.getSpec().getJobTemplate().getMetadata().getName()).isEqualTo("backup-template");
        assertThat(nightly.getSpec().getJobTemplate().getSpec().getActiveDeadlineSeconds()).isEqualTo(900L);
        assertThat(nightly.getSpec().getJobTemplate().getSpec().getTemplate().getSpec().getContainers().get(0)
                .getArgs()).containsExactly("sh", "-c", "echo backup");
        assertThat(nightly.getSpec().getJobTemplate().getSpec().getTemplate().getMetadata().getLabels())
                .containsEntry("job-template", "backup");
        assertThat(weekly.getSpec().getSchedule()).isEqualTo("0 2 * * 0");
        assertThat(tunedWeekly.getSpec().getJobTemplate().getSpec().getParallelism()).isEqualTo(2);
        assertThat(tunedWeekly.getSpec().getJobTemplate().getSpec().getActiveDeadlineSeconds()).isEqualTo(1800L);
        assertThat(weekly.getSpec().getJobTemplate().getSpec().getParallelism()).isEqualTo(1);
        assertThat(template.getSpec().getActiveDeadlineSeconds()).isEqualTo(900L);
    }

    @Test
    void listBuildersSupportMatchingEditingAndRemoval() {
        Job renderJob = jobNamed("render", "ml", "False");
        Job reportJob = jobNamed("report", "ml", "True");
        Job exportJob = jobNamed("export", "ops", "False");

        JobList jobs = new JobListBuilder()
                .withNewMetadata("next-page", 1L, "1024", "/apis/batch/v1/jobs")
                .addToItems(renderJob, reportJob)
                .addToItems(1, exportJob)
                .build();
        JobList editedJobs = new JobListBuilder(jobs)
                .editMatchingItem(item -> "report".equals(item.buildMetadata().getName()))
                .editStatus()
                .editFirstCondition()
                .withStatus("False")
                .withReason("Requeued")
                .endCondition()
                .endStatus()
                .endItem()
                .removeMatchingFromItems(item -> "ops".equals(item.buildMetadata().getNamespace()))
                .build();

        assertThat(jobs.getApiVersion()).isEqualTo(BATCH_V1_API_VERSION);
        assertThat(jobs.getKind()).isEqualTo("JobList");
        assertThat(jobs.getMetadata().getContinue()).isEqualTo("next-page");
        assertThat(jobs.getItems()).extracting(job -> job.getMetadata().getName())
                .containsExactly("render", "export", "report");
        assertThat(new JobListBuilder(jobs).hasMatchingItem(item -> "report".equals(item.buildMetadata().getName())))
                .isTrue();
        assertThat(editedJobs.getItems()).extracting(job -> job.getMetadata().getName())
                .containsExactly("render", "report");
        Job editedReport = new JobListBuilder(editedJobs)
                .buildMatchingItem(item -> "report".equals(item.buildMetadata().getName()));
        assertThat(editedReport.getStatus().getConditions().get(0).getStatus()).isEqualTo("False");
        assertThat(editedReport.getStatus().getConditions().get(0).getReason()).isEqualTo("Requeued");

        CronJob hourly = cronJobNamed("hourly", "*/60 * * * *");
        CronJob daily = cronJobNamed("daily", "0 0 * * *");
        CronJobList cronJobs = new CronJobListBuilder()
                .withNewMetadata("", 0L, "2048", "/apis/batch/v1/cronjobs")
                .addToItems(hourly, daily)
                .build();
        CronJobList editedCronJobs = new CronJobListBuilder(cronJobs)
                .editMatchingItem(item -> "daily".equals(item.buildMetadata().getName()))
                .editSpec()
                .withSuspend(true)
                .endSpec()
                .endItem()
                .build();

        assertThat(cronJobs.getApiVersion()).isEqualTo(BATCH_V1_API_VERSION);
        assertThat(cronJobs.getKind()).isEqualTo("CronJobList");
        assertThat(cronJobs.getItems()).extracting(cronJob -> cronJob.getMetadata().getName())
                .containsExactly("hourly", "daily");
        assertThat(editedCronJobs.getItems().get(1).getSpec().getSuspend()).isTrue();
        assertThat(cronJobs.getItems().get(1).getSpec().getSuspend()).isNull();
    }

    @Test
    void standalonePolicyBuildersSupportPredicatesAndAdditionalProperties() {
        PodFailurePolicyRule exitCodeRule = new PodFailurePolicyRule()
                .edit()
                .withAction("Count")
                .withNewOnExitCodes()
                .withContainerName("processor")
                .withOperator("NotIn")
                .withValues(0)
                .endOnExitCodes()
                .addToAdditionalProperties("scope", "container")
                .build();
        PodFailurePolicy policy = new PodFailurePolicyBuilder()
                .addToRules(exitCodeRule)
                .addNewRule()
                .withAction("Ignore")
                .addNewOnPodCondition()
                .withType("PodScheduled")
                .withStatus("False")
                .endOnPodCondition()
                .endRule()
                .build();
        PodFailurePolicy editedPolicy = new PodFailurePolicyBuilder(policy)
                .editMatchingRule(rule -> "Ignore".equals(rule.getAction()))
                .withAction("FailIndex")
                .endRule()
                .build();

        assertThat(policy.getRules()).hasSize(2);
        assertThat(policy.getRules().get(0).getAdditionalProperties()).containsEntry("scope", "container");
        PodFailurePolicyOnExitCodesRequirement exitCodes = policy.getRules().get(0).getOnExitCodes();
        assertThat(exitCodes.getOperator()).isEqualTo("NotIn");
        assertThat(exitCodes.getValues()).containsExactly(0);
        assertThat(policy.getRules().get(1).getOnPodConditions().get(0).getType()).isEqualTo("PodScheduled");
        assertThat(new PodFailurePolicyBuilder(policy).hasMatchingRule(rule -> "Ignore".equals(rule.getAction())))
                .isTrue();
        assertThat(editedPolicy.getRules().get(1).getAction()).isEqualTo("FailIndex");
        assertThat(policy.getRules().get(1).getAction()).isEqualTo("Ignore");

        SuccessPolicy successPolicy = new SuccessPolicyBuilder()
                .addNewRule(1, "0")
                .addNewRule()
                .withSucceededCount(2)
                .withSucceededIndexes("1-3")
                .addToAdditionalProperties("note", "partial success")
                .endRule()
                .build();
        SuccessPolicy reducedPolicy = new SuccessPolicyBuilder(successPolicy)
                .removeMatchingFromRules(rule -> "0".equals(rule.getSucceededIndexes()))
                .build();

        assertThat(successPolicy.getRules()).hasSize(2);
        assertThat(successPolicy.getRules().get(0).getSucceededCount()).isEqualTo(1);
        assertThat(successPolicy.getRules().get(0).getSucceededIndexes()).isEqualTo("0");
        assertThat(successPolicy.getRules().get(1).getAdditionalProperties()).containsEntry("note", "partial success");
        assertThat(reducedPolicy.getRules()).hasSize(1);
        assertThat(reducedPolicy.getRules().get(0).getSucceededIndexes()).isEqualTo("1-3");
    }

    @Test
    void v1beta1CronJobBuildersCreateOlderCronJobResources() {
        io.fabric8.kubernetes.api.model.batch.v1beta1.CronJob cronJob =
                new io.fabric8.kubernetes.api.model.batch.v1beta1.CronJobBuilder()
                        .withNewMetadata()
                        .withName("legacy-report")
                        .withNamespace("reports")
                        .endMetadata()
                        .withNewSpec()
                        .withSchedule("15 2 * * *")
                        .withConcurrencyPolicy("Replace")
                        .withStartingDeadlineSeconds(60L)
                        .withSuccessfulJobsHistoryLimit(2)
                        .withFailedJobsHistoryLimit(2)
                        .withSuspend(false)
                        .withNewJobTemplate()
                        .withNewMetadata()
                        .addToLabels("cronjob", "legacy-report")
                        .endMetadata()
                        .withNewSpec()
                        .withCompletions(1)
                        .withParallelism(1)
                        .withNewTemplate()
                        .withNewSpec()
                        .withRestartPolicy("Never")
                        .addNewContainer()
                        .withName("reporter")
                        .withImage("busybox:1.36")
                        .withArgs("date")
                        .endContainer()
                        .endSpec()
                        .endTemplate()
                        .endSpec()
                        .endJobTemplate()
                        .endSpec()
                        .withNewStatus()
                        .addNewActive()
                        .withApiVersion(BATCH_V1_API_VERSION)
                        .withKind("Job")
                        .withName("legacy-report-28650015")
                        .withNamespace("reports")
                        .endActive()
                        .withLastScheduleTime("2026-01-01T02:15:00Z")
                        .endStatus()
                        .build();
        io.fabric8.kubernetes.api.model.batch.v1beta1.CronJobList list =
                new io.fabric8.kubernetes.api.model.batch.v1beta1.CronJobListBuilder()
                        .withNewMetadata("", 0L, "3000", "/apis/batch/v1beta1/cronjobs")
                        .addToItems(cronJob)
                        .build();

        io.fabric8.kubernetes.api.model.batch.v1beta1.CronJob edited = cronJob.toBuilder()
                .editSpec()
                .withSuspend(true)
                .editJobTemplate()
                .editSpec()
                .withBackoffLimit(1)
                .endSpec()
                .endJobTemplate()
                .endSpec()
                .build();

        assertThat(cronJob.getApiVersion()).isEqualTo(BATCH_V1BETA1_API_VERSION);
        assertThat(cronJob.getKind()).isEqualTo("CronJob");
        assertThat(cronJob.getSpec().getSchedule()).isEqualTo("15 2 * * *");
        assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec().getContainers().get(0)
                .getName()).isEqualTo("reporter");
        assertThat(cronJob.getStatus().getActive()).hasSize(1);
        assertThat(cronJob.getStatus().getActive().get(0).getName()).isEqualTo("legacy-report-28650015");
        assertThat(list.getApiVersion()).isEqualTo(BATCH_V1BETA1_API_VERSION);
        assertThat(list.getKind()).isEqualTo("CronJobList");
        assertThat(list.getItems()).containsExactly(cronJob);
        assertThat(edited.getSpec().getSuspend()).isTrue();
        assertThat(edited.getSpec().getJobTemplate().getSpec().getBackoffLimit()).isEqualTo(1);
        assertThat(cronJob.getSpec().getJobTemplate().getSpec().getBackoffLimit()).isNull();
    }

    private static ObjectReference activeJobReference(String name, String uid) {
        return new ObjectReferenceBuilder()
                .withApiVersion(BATCH_V1_API_VERSION)
                .withKind("Job")
                .withNamespace("ops")
                .withName(name)
                .withUid(uid)
                .build();
    }

    private static Job jobNamed(String name, String namespace, String completeStatus) {
        JobCondition condition = new JobCondition()
                .edit()
                .withType("Complete")
                .withStatus(completeStatus)
                .withReason("Fixture")
                .build();
        return new JobBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withParallelism(1)
                .withNewTemplate()
                .withNewSpec()
                .withRestartPolicy("Never")
                .addNewContainer()
                .withName(name + "-container")
                .withImage("busybox:1.36")
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .withNewStatusLike(new JobStatusBuilder().withConditions(condition).build())
                .endStatus()
                .build();
    }

    private static CronJob cronJobWithTemplate(String name, String schedule, JobTemplateSpec template) {
        return new CronJobBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace("ops")
                .endMetadata()
                .withNewSpec()
                .withSchedule(schedule)
                .withJobTemplate(template)
                .endSpec()
                .build();
    }

    private static CronJob cronJobNamed(String name, String schedule) {
        return new CronJobBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace("ops")
                .endMetadata()
                .withNewSpec()
                .withSchedule(schedule)
                .withNewJobTemplate()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .withRestartPolicy("Never")
                .addNewContainer()
                .withName(name + "-container")
                .withImage("busybox:1.36")
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .endJobTemplate()
                .endSpec()
                .build();
    }
}
