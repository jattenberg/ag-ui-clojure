# Protocol research

Sources consulted (2026-09-06):

- https://docs.ag-ui.com/llms.txt
- https://docs.ag-ui.com/spec/draft/ (behavioural 1.0 **draft**, not ratified)
- https://ag-ui.com/spec/draft/schema.json (pinned at `spec/draft/schema.json`)
- https://docs.ag-ui.com/concepts/* (0.x / conceptual docs still published)
- https://docs.ag-ui.com/sdk/js/core/events.md and `/types.md`
- https://docs.ag-ui.com/sdk/python/encoder/overview.md
- https://docs.ag-ui.com/quickstart/server.md
- https://github.com/ag-ui-protocol/ag-ui

This implementation treats **JSON Schema + the 1.0 draft behavioural pages** as the structural/behavioural target for an independent interpreter, and treats **existing TypeScript/Python SDKs** as interoperability peers. Where 0.x docs, SDKs, and the 1.0 draft disagree, the disagreement is recorded rather than silently “fixed.”

Labels used below: **SPECIFIED**, **IMPLIED**, **IMPLEMENTATION CONVENTION**, **AMBIGUOUS**, **UNKNOWN**.

## What AG-UI is

**SPECIFIED (conceptual docs + draft):** an event protocol between a user-facing application (consumer) and an agentic backend (producer). One `RunAgentInput` in; an ordered stream of typed events out.

**SPECIFIED (draft architecture):** transport-agnostic at the protocol layer. HTTP+SSE is the default binding; HTTP+Protobuf is specified in the 1.0 draft.

## Canonical event types

**SPECIFIED (schema `EventType`, 31 values):**

Lifecycle: `RUN_STARTED`, `RUN_FINISHED`, `RUN_ERROR`, `STEP_STARTED`, `STEP_FINISHED`

Text: `TEXT_MESSAGE_START`, `TEXT_MESSAGE_CONTENT`, `TEXT_MESSAGE_END`, `TEXT_MESSAGE_CHUNK`

Tools: `TOOL_CALL_START`, `TOOL_CALL_ARGS`, `TOOL_CALL_END`, `TOOL_CALL_CHUNK`, `TOOL_CALL_RESULT`

State: `STATE_SNAPSHOT`, `STATE_DELTA`, `MESSAGES_SNAPSHOT`

Activity: `ACTIVITY_SNAPSHOT`, `ACTIVITY_DELTA`

Passthrough: `RAW`, `CUSTOM`

Reasoning: `REASONING_START`, `REASONING_MESSAGE_START`, `REASONING_MESSAGE_CONTENT`, `REASONING_MESSAGE_END`, `REASONING_MESSAGE_CHUNK`, `REASONING_END`, `REASONING_ENCRYPTED_VALUE`

Subagents: `SUBAGENT_STARTED`, `SUBAGENT_FINISHED`, `SUBAGENT_ERROR`

**AMBIGUOUS vs 0.x conceptual docs:** CopilotKit “architecture” pages still describe “~16” events and omit chunks, reasoning, activity, subagents, `TOOL_CALL_RESULT`. That is documentation lag, not a second protocol.

**SPECIFIED (1.0 changelog):** 0.x `THINKING_*` is retired in favour of reasoning; consumers are supposed to translate at a compatibility boundary, not drop.

## Event fields and serialization

**SPECIFIED (schema):** wire field names are **camelCase** (`threadId`, `messageId`, `toolCallId`, …). Objects are closed (`unevaluatedProperties: false`) except open-by-design maps (`metadata`, JSON Patch ops, unconstrained state).

**SPECIFIED (Python encoder docs):** SSE `data:` payload is JSON with camelCase even when Python models use snake_case internally (`message_id` → `"messageId"`).

**SPECIFIED (draft event model):** optional fields are omitted, never `null`, except fields whose schema admits any JSON (`rawEvent`, `result`, `CUSTOM.value`, …). `timestamp` is an integer; unit is **not** constrained in schema. **IMPLIED / IMPLEMENTATION CONVENTION:** SDKs use milliseconds since Unix epoch.

**SPECIFIED:** `type` is required. Arrival order is protocol order; consumers MUST NOT sort by `timestamp`.

## Event ordering / run lifecycle

**SPECIFIED (draft lifecycle):**

- A stream MUST begin with `RUN_STARTED` or `RUN_ERROR`.
- A `RUN_STARTED` while a run is active is a violation.
- A run ends with `RUN_FINISHED` or `RUN_ERROR`.
- After close: only a new `RUN_STARTED`, or a late `RUN_ERROR` after `RUN_FINISHED`.
- After `RUN_ERROR`: only `RUN_STARTED`.
- Everything opened in a run MUST be closed before `RUN_FINISHED`; `RUN_ERROR` ends open items with the run.
- Several runs MAY appear on one stream (replay).

**SPECIFIED:** `RUN_ERROR` is a well-formed failure report, not a protocol violation. Protocol violations (bad sequencing, malformed known fields) are a different class.

## Messages

**SPECIFIED:** streamed text uses open/content/close matched by `messageId`. Deltas concatenate in arrival order. Empty `delta` is legal (keep-alive). Absent `role` on start means `assistant`. A closed message MAY be reopened with a new `START` for the same id (append). Roles for streamed text: `developer|system|assistant|user` (not `tool`).

**SPECIFIED:** `MESSAGES_SNAPSHOT` reconciles by id (replace in place; append unseen; drop missing except client-only activity/reasoning unless the snapshot itself includes those roles).

## Tool-call lifecycle

**SPECIFIED:** `START` → zero or more `ARGS` → `END`, matched by `toolCallId`. Do not act on args before `END`. Args are text, not validated JSON.

**SPECIFIED:** `TOOL_CALL_RESULT` is its own tool message (`messageId` + `toolCallId` + `content`); it does not reopen the call.

**SPECIFIED:** frontend tools advertised in `RunAgentInput.tools` are executed by the application across a run boundary (next input’s tool messages). Agent-side tools MAY result in-stream.

**AMBIGUOUS (0.x vs 1.0):** conceptual docs often stop at START/ARGS/END and never mention `TOOL_CALL_RESULT`.

## State snapshots and deltas

**SPECIFIED:** `STATE_SNAPSHOT` replaces. `STATE_DELTA` is RFC 6902, applied atomically against current state (input `state` or `{}` if absent, then snapshots/deltas).

**SPECIFIED:** malformed patch structure is fatal; well-formed patch that fails to apply → warn, keep prior value, do not fail the run. Unrecognised `op` is unrecognised union member (drop that op with warning in the processing model).

## Interrupts / HITL

**SPECIFIED (draft + concepts/interrupts):** `RUN_FINISHED.outcome` is optional. Absent ≡ success. `{type:"interrupt", interrupts:[...]}` ends the run. Resume is a **new** run with `RunAgentInput.resume[]`. Same `threadId`; cover all open interrupts; pending interrupts block other input.

**SPECIFIED:** emit state/messages snapshots before an interrupt `RUN_FINISHED`.

**IMPLEMENTATION CONVENTION:** LangGraph integration emits structured interrupt outcomes behind an opt-in flag; legacy `CUSTOM` `on_interrupt` still exists.

## Errors

**SPECIFIED:** in-stream `RUN_ERROR` after the SSE response has started. HTTP error status with **no** stream if input is rejected before the run starts.

**SPECIFIED:** truncated connection without a terminal event is a truncated run.

## Custom / raw

**SPECIFIED:** `CUSTOM` requires `name` and `value`. Unrecognised names are ignored without warning. Unprefixed names reserved for future protocol use. `RAW` must not drive protocol behaviour.

## SSE / HTTP

**SPECIFIED (draft http-sse):**

- `POST` JSON `RunAgentInput`, `Content-Type: application/json`, `Accept: text/event-stream`
- `200` + `text/event-stream`
- One protocol event per SSE `data` payload
- Producer frames with LF
- Ignore `event:`, `id:`, `retry:`; comments allowed
- No `Last-Event-ID` resumption

**IMPLEMENTATION CONVENTION (quickstart):** many servers also accept `POST /`. Media type may follow encoder `Accept` negotiation (SSE vs protobuf).

## Transports

**SPECIFIED:** HTTP+SSE default; HTTP+Protobuf in 1.0 draft; custom transports must preserve ordered events.

**UNKNOWN in this repo:** protobuf frame corpus is not reimplemented (JSON/SSE only).

## Processing model

**SPECIFIED:** unrecognised event types/fields/union members survive to enforcement then drop/strip with warning. Malformed known values are fatal. Middleware before enforcement; chunk expansion before verification.

**SPECIFIED:** authoring against the strict schema is **not** the same as runtime receive. This repo exposes both `:authoring` and `:runtime` validation modes.

## Existing conformance tests / fixtures

**UNKNOWN / not found as a standalone published suite:** the upstream monorepo contains SDK tests and a “dojo,” but no language-independent fixture pack equivalent to `fixtures/` here was documented as a conformance product. That gap is a primary motivation for this project.

## Existing implementations

**SPECIFIED (docs):** first-party SDKs TypeScript, Python, .NET. Community SDKs: Kotlin, Go, Dart, Java, Rust, Ruby, C++, others. Clients: CopilotKit, terminal, Channels SDK.

This Clojure project is **not** an official SDK.

## Interoperability strategy

1. Wire JSON matches schema camelCase (verified against Python encoder documentation examples).
2. HTTP+SSE matches the draft binding example.
3. Live tests: Clojure client ↔ Clojure echo server (sanity); Clojure client ↔ Python `ag-ui-protocol` encoder/server when installed; external HttpAgent/curl ↔ Clojure server.
