# Conformance (this directory)

See [`../docs/conformance.md`](../docs/conformance.md) and [`../fixtures/`](../fixtures/).

```bash
clojure -M:conformance
bb conformance
```

Fixture format is **wire JSON** (camelCase), one event per JSONL line, so non-Clojure implementations can consume them without this runtime.
