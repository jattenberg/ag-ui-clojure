# Architecture

Layers, bottom-up. HTTP is optional; protocol code does not import http-kit.

```
agent / fixture / test
        ↓
   event seq (ag-ui.stream)
        ↓
   validate / chunks / invariants / reduce
        ↓
   JSON (ag-ui.serialization.json)
        ↓
   SSE (ag-ui.sse)
        ↓
   HTTP (ag-ui.server.http / ag-ui.client.http)
```

## Internal events

Plain maps, kebab-case keys, string `:type`:

```clojure
{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
```

Wire JSON is camelCase. Conversion is explicit in `ag-ui.serialization.fields`.

Opaque JSON (`snapshot`, `state`, `metadata`, `CUSTOM.value`, patch `value`) keeps **string keys** so RFC 6902 pointers match the document.

## Validation

`(ag-ui.protocol.validate/validate-event event)`
`(ag-ui.protocol.validate/validate-event event {:mode :runtime})`

- `:authoring` — closed objects, unknown types fail (schema / fixtures).
- `:runtime` — processing model: drop unknown types, strip unknown properties, fail malformed known values.

Lifecycle is separate: `ag-ui.protocol.invariants`.

## Streams

`ag-ui.stream/event-seq` accepts a seq, delay, or thunk. No core.async.

## Server

`ag-ui.server.echo/events-for` is a pure function of `RunAgentInput`.
`ag-ui.server.http` POSTs `/` or `/agent` and writes SSE.

## Client

`ag-ui.client.http/run-agent` returns decoded items, validated events, and a reduced state. Failures are tagged `:transport`, `:http`, or `:protocol`.

## Dual runtime

JVM uses `clojure.data.json`; Babashka uses built-in Cheshire for JSON (data.json 2.5 is not SCI-compatible). http-kit is available in both.

| Surface | JVM (`clojure -M:…`) | Babashka (`bb …`) |
| --- | --- | --- |
| Conformance CLI | yes | yes |
| Echo SSE server | preferred for long runs | probe |
| HTTP client | yes | yes |
| `clojure.test` unit tests | yes | yes |
| `test.check` property tests | yes | no |

Entry: [`bb.edn`](../bb.edn). Property tests live in `*_properties_test.clj` namespaces so `bb test` never loads `test.check`.

## Conformance profiles

`ag-ui.conformance.core/check-valid-stream` takes `:producer` or `:consumer`.

- Producer: translate `THINKING_*`, authoring validation, pinned JSON Schema (JVM), chunk expand, lifecycle, reduce, JSON round-trip.
- Consumer: same pipeline with runtime validation (drop unknown types, strip unknown fields). Schema is skipped.

## Echo agent

`ag-ui.server.echo/events-for` is a pure function of `RunAgentInput`. Keywords in the last user message select a protocol family (`tool`, `state`, `interrupt`, `error`, `custom`, `activity`, `subagent`, `reasoning`, `encrypted`, `snapshot`, `chunk`). A non-empty `:resume` vector emits a success run.

## HTTP

- `POST /` or `POST /agent` with `Accept: text/event-stream` → `200` SSE.
- Missing Accept → `406`.
- Malformed `RunAgentInput` → `400` and no stream.
- `GET /health` → `ok`.

