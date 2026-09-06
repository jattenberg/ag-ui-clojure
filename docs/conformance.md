# Conformance

The suite is meant to be reusable by other implementations, not only Clojure tests.

Index: [`fixtures/manifest.json`](../fixtures/manifest.json). How to consume without Clojure: [`fixtures/README.md`](../fixtures/README.md).

Two **profiles** (see the processing model vs JSON Schema split in the draft):

- **producer** / authoring — closed objects, unknown types fail, JVM checks `spec/draft/schema.json`
- **consumer** / runtime — unknown types are dropped, unknown fields stripped; malformed known values still fail

A stream may `accept` on one profile and `reject` on the other (`future-event`, `extra-property`).

## CLI

```bash
clojure -M:conformance
clojure -M:conformance --profile all
clojure -M:conformance --profile producer
clojure -M:conformance --profile consumer
clojure -M:conformance --endpoint http://127.0.0.1:8000/
bb conformance --profile all
AG_UI_EXTERNAL_URL=http://127.0.0.1:18090/ clojure -M:conformance
```

Default profile is `all`.

## CI

```bash
bash script/ci.sh
```

GitHub Actions (`.github/workflows/ci.yml`) runs that script: Babashka tests, JVM tests, both conformance profiles, live Clojure echo (client, Node `fetch`, `@ag-ui/client`), and official Python `ag-ui-protocol` encoder + echo server.

## Tests

```bash
clojure -M:test
bb test
```

Property tests (`test.check`) run only on the JVM.
