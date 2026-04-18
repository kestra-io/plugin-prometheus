# Kestra Prometheus Plugin

## What

- Provides plugin components under `io.kestra.plugin.prometheus`.
- Includes classes such as `Trigger`, `Push`, `Query`.

## Why

- This plugin integrates Kestra with Prometheus.
- It provides tasks that query Prometheus and push custom metrics.

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
