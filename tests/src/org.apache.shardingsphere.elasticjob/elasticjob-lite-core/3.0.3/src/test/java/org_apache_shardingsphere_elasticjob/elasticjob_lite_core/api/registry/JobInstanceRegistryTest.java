/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.api.registry;

import org.apache.shardingsphere.elasticjob.api.JobConfiguration;
import org.apache.shardingsphere.elasticjob.infra.handler.sharding.JobInstance;
import org.apache.shardingsphere.elasticjob.infra.pojo.JobConfigurationPOJO;
import org.apache.shardingsphere.elasticjob.infra.yaml.YamlEngine;
import org.apache.shardingsphere.elasticjob.lite.api.registry.JobInstanceRegistry;
import org.apache.shardingsphere.elasticjob.reg.base.CoordinatorRegistryCenter;
import org.apache.shardingsphere.elasticjob.reg.listener.DataChangedEvent;
import org.apache.shardingsphere.elasticjob.reg.listener.DataChangedEvent.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public final class JobInstanceRegistryTest {

    @Mock
    private CoordinatorRegistryCenter regCenter;

    @Test
    public void assertListenWithoutConfigPath() {
        JobInstanceRegistry jobInstanceRegistry = new JobInstanceRegistry(regCenter, new JobInstance("id"));
        jobInstanceRegistry.new JobInstanceRegistryListener().onChange(new DataChangedEvent(Type.ADDED, "/jobName", ""));
        verify(regCenter, times(0)).get("/jobName");
    }

    @Test
    public void assertListenLabelNotMatch() {
        JobInstanceRegistry jobInstanceRegistry = new JobInstanceRegistry(regCenter, new JobInstance("id", "label1,label2"));
        String jobConfig = toYaml(JobConfiguration.newBuilder("jobName", 1).label("label").build());
        jobInstanceRegistry.new JobInstanceRegistryListener().onChange(new DataChangedEvent(Type.ADDED, "/jobName/config", jobConfig));
        verify(regCenter, times(0)).get("/jobName");
    }

    @Test
    public void assertListenScheduleJob() {
        assertThrows(RuntimeException.class, () -> {
            JobInstanceRegistry jobInstanceRegistry = new JobInstanceRegistry(regCenter, new JobInstance("id"));
            String jobConfig = toYaml(JobConfiguration.newBuilder("jobName", 1).cron("0/1 * * * * ?").label("label").build());
            jobInstanceRegistry.new JobInstanceRegistryListener().onChange(new DataChangedEvent(Type.ADDED, "/jobName/config", jobConfig));
        });
    }

    @Test
    public void assertListenOneOffJob() {
        assertThrows(RuntimeException.class, () -> {
            JobInstanceRegistry jobInstanceRegistry = new JobInstanceRegistry(regCenter, new JobInstance("id", "label"));
            String jobConfig = toYaml(JobConfiguration.newBuilder("jobName", 1).label("label").build());
            jobInstanceRegistry.new JobInstanceRegistryListener().onChange(new DataChangedEvent(Type.ADDED, "/jobName/config", jobConfig));
        });
    }

    private String toYaml(final JobConfiguration build) {
        return YamlEngine.marshal(JobConfigurationPOJO.fromJobConfiguration(build));
    }
}
