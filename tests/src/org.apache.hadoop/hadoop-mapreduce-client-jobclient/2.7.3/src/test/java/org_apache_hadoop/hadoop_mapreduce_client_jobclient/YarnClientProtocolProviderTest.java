/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_mapreduce_client_jobclient;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.ipc.ProtocolSignature;
import org.apache.hadoop.mapred.YarnClientProtocolProvider;
import org.apache.hadoop.mapreduce.Cluster.JobTrackerStatus;
import org.apache.hadoop.mapreduce.ClusterMetrics;
import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.JobID;
import org.apache.hadoop.mapreduce.JobStatus;
import org.apache.hadoop.mapreduce.QueueAclsInfo;
import org.apache.hadoop.mapreduce.QueueInfo;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.TaskCompletionEvent;
import org.apache.hadoop.mapreduce.TaskReport;
import org.apache.hadoop.mapreduce.TaskTrackerInfo;
import org.apache.hadoop.mapreduce.TaskType;
import org.apache.hadoop.mapreduce.protocol.ClientProtocol;
import org.apache.hadoop.mapreduce.security.token.delegation.DelegationTokenIdentifier;
import org.apache.hadoop.mapreduce.v2.LogParams;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.authorize.AccessControlList;
import org.apache.hadoop.security.token.Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class YarnClientProtocolProviderTest {
    @Test
    void directProtocolCreationReturnsNullForLocalFramework() throws Exception {
        YarnClientProtocolProvider provider = new YarnClientProtocolProvider();
        Configuration configuration = localMapReduceConfiguration();

        ClientProtocol protocol = provider.create(configuration);

        assertThat(protocol).isNull();
        provider.close(protocol);
    }

    @Test
    void closeIgnoresNonYarnClientProtocol() throws Exception {
        YarnClientProtocolProvider provider = new YarnClientProtocolProvider();
        ThrowingClientProtocol protocol = new ThrowingClientProtocol();

        provider.close(protocol);

        assertThat(protocol.wasUsed()).isFalse();
    }

    @Test
    void addressedProtocolCreationReturnsNullForLocalFramework() throws Exception {
        YarnClientProtocolProvider provider = new YarnClientProtocolProvider();
        Configuration configuration = localMapReduceConfiguration();
        InetSocketAddress address = InetSocketAddress.createUnresolved("localhost", 8032);

        ClientProtocol protocol = provider.create(address, configuration);

        assertThat(protocol).isNull();
        provider.close(protocol);
    }

    private static Configuration localMapReduceConfiguration() {
        Configuration configuration = new Configuration(false);
        configuration.set("mapreduce.framework.name", "local");
        return configuration;
    }

    private static final class ThrowingClientProtocol implements ClientProtocol {
        private boolean used;

        boolean wasUsed() {
            return used;
        }

        @Override
        public JobID getNewJobID() throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public JobStatus submitJob(JobID jobId, String jobSubmitDir, Credentials ts)
                throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public ClusterMetrics getClusterMetrics() throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public JobTrackerStatus getJobTrackerStatus() throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public long getTaskTrackerExpiryInterval() throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public AccessControlList getQueueAdmins(String queueName) throws IOException {
            throw unused();
        }

        @Override
        public void killJob(JobID jobid) throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public void setJobPriority(JobID jobid, String priority)
                throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public boolean killTask(TaskAttemptID taskId, boolean shouldFail)
                throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public JobStatus getJobStatus(JobID jobid) throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public Counters getJobCounters(JobID jobid) throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public TaskReport[] getTaskReports(JobID jobid, TaskType type)
                throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public String getFilesystemName() throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public JobStatus[] getAllJobs() throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public TaskCompletionEvent[] getTaskCompletionEvents(
                JobID jobid,
                int fromEventId,
                int maxEvents) throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public String[] getTaskDiagnostics(TaskAttemptID taskId)
                throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public TaskTrackerInfo[] getActiveTrackers() throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public TaskTrackerInfo[] getBlacklistedTrackers() throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public String getSystemDir() throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public String getStagingAreaDir() throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public String getJobHistoryDir() throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public QueueInfo[] getQueues() throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public QueueInfo getQueue(String queueName) throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public QueueAclsInfo[] getQueueAclsForCurrentUser()
                throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public QueueInfo[] getRootQueues() throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public QueueInfo[] getChildQueues(String queueName)
                throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public Token<DelegationTokenIdentifier> getDelegationToken(Text renewer)
                throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public long renewDelegationToken(Token<DelegationTokenIdentifier> token)
                throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public void cancelDelegationToken(Token<DelegationTokenIdentifier> token)
                throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public LogParams getLogFileParams(JobID jobID, TaskAttemptID taskAttemptID)
                throws IOException, InterruptedException {
            throw unused();
        }

        @Override
        public long getProtocolVersion(String protocol, long clientVersion) throws IOException {
            throw unused();
        }

        @Override
        public ProtocolSignature getProtocolSignature(
                String protocol,
                long clientVersion,
                int clientMethodsHash) throws IOException {
            throw unused();
        }

        private AssertionError unused() {
            used = true;
            return new AssertionError("Non-YARN protocols must not be used when closing");
        }
    }
}
