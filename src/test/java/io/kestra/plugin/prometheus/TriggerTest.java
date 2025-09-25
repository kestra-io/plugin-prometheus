package io.kestra.plugin.prometheus;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.Worker;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.runner.JdbcScheduler;
import io.kestra.scheduler.AbstractScheduler;
import io.kestra.worker.DefaultWorker;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
public class TriggerTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    private FlowListeners flowListenersService;

    @Inject
    private LocalFlowRepositoryLoader localFlowRepositoryLoader;

    @Test
    void run() throws Exception {
        Execution execution = triggerFlow();

        List<Map<String, Object>> metrics = (List<Map<String, Object>>) execution.getTrigger().getVariables().get("metrics");

        assertThat(metrics.size(), greaterThan(0));
        assertThat(metrics.getFirst().get("value"), is("150"));
        assertThat(metrics.getFirst().get("labels").toString(), containsString("trigger_test"));
    }

    protected Execution triggerFlow() throws Exception {
        CountDownLatch queueCount = new CountDownLatch(1);

        try (
            AbstractScheduler scheduler = new JdbcScheduler(applicationContext, flowListenersService);
            DefaultWorker worker = applicationContext.createBean(DefaultWorker.class, IdUtils.create(), 8, null)
        ) {
            Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {queueCount.countDown();assertThat(execution.getLeft().getFlowId(), is("prometheus-listen"));});

            worker.run();
            scheduler.run();

            localFlowRepositoryLoader.load(Objects.requireNonNull(this.getClass().getClassLoader().getResource("flows/prometheus-listen.yml")));

            boolean await = queueCount.await(1, TimeUnit.MINUTES);
            assertThat(await, is(true));

            return receive.blockLast();
        }
    }
}