package io.kestra.plugin.prometheus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.EvaluateTrigger;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
=======
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.runner.JdbcScheduler;
import io.kestra.scheduler.AbstractScheduler;
import io.kestra.worker.DefaultWorker;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
>>>>>>> 63b0fe8 (fix: v2 compatibility)

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
public class TriggerTest {
<<<<<<< HEAD

    @SuppressWarnings("unchecked")
    @Test
    @EvaluateTrigger(flow = "flows/prometheus-listen.yml", triggerId = "watch")
    void run(Optional<Execution> optionalExecution) {
        assertThat(optionalExecution.isPresent(), is(true));

        Execution execution = optionalExecution.get();
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) execution.getTrigger().getVariables().get("metrics");

        assertThat(metrics.size(), greaterThan(0));
        assertThat(metrics.getFirst().get("value"), is("150"));
        assertThat(metrics.getFirst().get("labels").toString(), containsString("trigger_test"));
    }
<<<<<<< HEAD
=======

    protected Execution triggerFlow() throws Exception {
        CountDownLatch queueCount = new CountDownLatch(1);
        AtomicReference<Execution> lastExecution = new AtomicReference<>();

        try (
            AbstractScheduler scheduler = new JdbcScheduler(applicationContext, flowListenersService);
            DefaultWorker worker = applicationContext.createBean(DefaultWorker.class, IdUtils.create(), 8, null)
        ) {
            executionQueue.addListener(execution ->
            {
                lastExecution.set(execution);
                queueCount.countDown();
                assertThat(execution.getFlowId(), is("prometheus-listen"));
            });

            worker.run();
            scheduler.run();

            localFlowRepositoryLoader.load(Objects.requireNonNull(this.getClass().getClassLoader().getResource("flows/prometheus-listen.yml")));

            boolean await = queueCount.await(1, TimeUnit.MINUTES);
            assertThat(await, is(true));

            return lastExecution.get();
        }
    }
>>>>>>> 63b0fe8 (fix: v2 compatibility)
}
