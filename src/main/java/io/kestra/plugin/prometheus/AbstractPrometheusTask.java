package io.kestra.plugin.prometheus;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.BasicAuthConfiguration;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractPrometheusTask<T extends io.kestra.core.models.tasks.Output> extends Task implements RunnableTask<T> {
    @Schema(
        title = "Username",
        description = "Optional basic auth username."
    )
    protected Property<String> username;

    @Schema(
        title = "Password",
        description = "Optional basic auth password."
    )
    protected Property<String> password;

    @Schema(
        title = "HTTP headers",
        description = "HTTP headers to include in the request"
    )
    public Property<Map<String,String>> headers;

    @Schema(
        title = "HTTP options",
        description = "HTTP client configuration."
    )
    protected HttpConfiguration options;

    protected abstract HttpRequest buildRequest(RunContext runContext) throws Exception;

    protected abstract T handleResponse(RunContext runContext, HttpResponse<String> response) throws Exception;

    public T run(RunContext runContext) throws Exception {
        HttpRequest request = buildRequest(runContext);

        var optionsBuilder = options != null ? options.toBuilder() : HttpConfiguration.builder();

        if (this.username != null && this.password != null) {
            optionsBuilder.auth(
                BasicAuthConfiguration.builder()
                    .username(this.username)
                    .password(this.password)
                    .build()
            );
        }

        HttpConfiguration httpConfig = optionsBuilder.build();

        try (HttpClient client = new HttpClient(runContext, httpConfig)) {
            HttpResponse<String> response = client.request(request, String.class);

            if (response.getStatus().getCode() >= 400) {
                throw new RuntimeException("Prometheus call failed: " + response.getBody());
            }

            return handleResponse(runContext, response);
        }
    }
}
