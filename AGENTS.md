# Kestra Prometheus Plugin

## What

- Provides plugin components under `io.kestra.plugin.prometheus`.
- Includes classes such as `Trigger`, `Push`, `Query`.

## Why

- What user problem does this solve? Teams need to query Prometheus and push custom metrics from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Prometheus steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Prometheus.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `prometheus`

Infrastructure dependencies (Docker Compose services):

- `prometheus`
- `pushgateway`

### Key Plugin Classes

- `io.kestra.plugin.prometheus.Push`
- `io.kestra.plugin.prometheus.Query`
- `io.kestra.plugin.prometheus.Trigger`

### Project Structure

```
plugin-prometheus/
├── src/main/java/io/kestra/plugin/prometheus/
├── src/test/java/io/kestra/plugin/prometheus/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
