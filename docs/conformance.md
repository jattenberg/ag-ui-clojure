# Conformance

The suite is meant to be reusable by other implementations, not only Clojure tests.

Index: [`fixtures/manifest.json`](../fixtures/manifest.json). How to consume without Clojure: [`fixtures/README.md`](../fixtures/README.md).

## CLI

```bash
clojure -M:conformance
clojure -M:conformance --endpoint http://127.0.0.1:8000/
bb conformance
AG_UI_EXTERNAL_URL=http://127.0.0.1:18090/ clojure -M:conformance
```

The CLI:

1. Loads streams listed in the manifest.
2. Translates retired `THINKING_*` events, then validates structure.
3. On the JVM, validates each event against `spec/draft/schema.json` (JSON Schema 2020-12). Babashka skips this stage.
4. Expands chunks, checks lifecycle (including reasoning spans, subagents, resume coverage when `RUN_STARTED.input` is present), round-trips JSON, reduces state.
5. Asserts malformed fixtures are rejected.
6. Checks `fixtures/runs/*resume*.json` coverage cases.
7. Optionally POSTs to a live SSE endpoint (`--endpoint` or `AG_UI_EXTERNAL_URL`).

## Tests

```bash
clojure -M:test
bb test
```

Property tests (`test.check`) run only on the JVM.
