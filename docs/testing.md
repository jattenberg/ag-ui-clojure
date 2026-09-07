# Testing

## Fast loop

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk   # macOS Homebrew OpenJDK, if needed
bb test
clojure -M:test
clojure -M:conformance --profile all
```

| Command | What it covers |
| --- | --- |
| `bb test` | SCI unit tests (no `test.check`, no JSON Schema) |
| `clojure -M:test` | All `clojure.test` + property tests + schema tests |
| `clojure -M:conformance --profile all` | Manifest streams as producer and consumer |
| `bash script/ci.sh` | The above, plus live Clojure echo, `@ag-ui/client`, Python `ag-ui-protocol` |

GitHub Actions runs `script/ci.sh` on every push and pull request (`.github/workflows/ci.yml`).

## What “thorough” means here

- **Structure:** authoring vs runtime validation (`ag-ui.protocol.validate-test`).
- **Lifecycle:** open/close, nested subagents, resume coverage (`invariants`, `resume`, `chunks`).
- **State:** text/tool/activity accumulation, JSON Patch, snapshots (`reduce-test`).
- **Wire:** camelCase round-trip, opaque snapshot keys, SSE framing (`json`, `fields`, `sse`).
- **Fixtures:** every `fixtures/manifest.json` stream against both profiles.
- **HTTP:** echo server, malformed input, `406` without `Accept: text/event-stream`, `/health`, Mochi Protocol Zoo `GET /`.
- **Properties (JVM):** generated text runs stay legal; `RUN_STARTED` and `CUSTOM` JSON round-trip.

## Echo keywords

The deterministic agent maps user text to protocol families. Unit tests cover `events-for`; HTTP tests cover the default hello path and `activity`.
