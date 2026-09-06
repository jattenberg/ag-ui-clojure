# Specification ambiguities

Independent implementation notes. Nothing here is silently “fixed” in code without a pointer.

## Ambiguity: 0.x conceptual docs vs 1.0 draft schema

### Specification

Published conceptual pages still describe roughly sixteen events. `schema.json` and the 1.0 draft enumerate 31 `EventType` values, including chunks, reasoning, activity, and subagents. The draft itself says it is **not ratified** and must not be cited as a stable reference.

### Question

Which surface is the interoperability target for a new implementation in 2026?

### Possible interpretations

A. Implement only the 0.x conceptual set (what many production integrations still emit).  
B. Implement the draft schema (what first-party SDK type docs currently list).

### Current implementation

B for structure and sequencing, with 0.x documented as a subset. Unknown types are dropped at **runtime**, rejected in **authoring** fixtures.

### Interoperability impact

A strict 0.x consumer will not understand `TEXT_MESSAGE_CHUNK`, `TOOL_CALL_RESULT`, reasoning, activity, or subagent events. A draft consumer must still accept streams that never send them.

### Recommendation

Publish a frozen 0.x vs 1.0 compatibility table in the official spec, and version `RunAgentInput.protocolVersion` / `RUN_STARTED.protocolVersion` in all first-party servers.

---

## Ambiguity: timestamp unit

### Specification

Schema: integer, JSON-safe range. Prose: unit is not constrained; SDKs use Unix milliseconds.

### Question

Is a producer that sends Unix seconds conformant?

### Possible interpretations

A. Any integer is valid.  
B. Milliseconds are required in practice.

### Current implementation

Accept any integer; do not order by it.

### Interoperability impact

UI clocks will be wrong if peers disagree.

### Recommendation

State the unit as milliseconds in the schema description and make it normative.

---

## Ambiguity: unknown event types at authoring time vs runtime

### Specification

Processing model: unknown types MUST NOT abort a run. Schema: `EventType` is a closed enum.

### Question

Should a conformance fixture pack reject `NOT_A_REAL_EVENT`?

### Possible interpretations

A. Runtime consumer: drop with warning.  
B. Producer/authoring: invalid.

### Current implementation

Both modes exist. `fixtures/malformed/unknown-event-type.json` is invalid for **producers**.

### Interoperability impact

A suite that only tests runtime receive will green-pass illegal producers.

### Recommendation

Split conformance into producer (strict schema) and consumer (processing model) profiles.

---

## Ambiguity: `TEXT_MESSAGE_CONTENT` with zero content events

### Specification

Streaming pattern: zero or more content events between start and end.

### Question

Is `START` + `END` with no `CONTENT` a valid empty message?

### Possible interpretations

A. Yes (zero or more).  
B. Some UIs assume at least one delta.

### Current implementation

A. Empty messages are valid. Concatenation of no deltas is `""`.

### Interoperability impact

Clients that wait for the first content event may never render the bubble.

### Recommendation

State that empty messages are legal and that UIs should create the message on `START`.

---

## Ambiguity: JSON Patch document key types after language mapping

### Specification

Patches apply to JSON documents. Language SDKs often keywordize keys.

### Question

Does `/draft/title` address a Clojure `:draft` key?

### Possible interpretations

A. Pointers always address JSON string keys.  
B. SDKs may rewrite pointers.

### Current implementation

A. Snapshots/state keep string keys.

### Interoperability impact

A keywordizing consumer will silently fail patches (and then warn/skip per spec).

### Recommendation

Add a note: implementations that intern keys must intern patch paths the same way, or not intern state keys.

---

## Ambiguity: `RUN_FINISHED` without `outcome` vs `{type:"success"}`

### Specification

Absent outcome means success. Explicit success is also valid.

### Question

Must producers emit `outcome`?

### Possible interpretations

A. Omit for 0.x compatibility.  
B. Always send `{type:"success"}`.

### Current implementation

Echo agent omits `outcome` on success. Reducer synthesizes `{:type "success"}` internally.

### Interoperability impact

Clients that require `outcome` break on 0.x servers (the draft says they must not).

### Recommendation

Keep absence as success; add a test vector for both spellings (this repo’s interrupt fixture uses the explicit interrupt variant only).

---

## Ambiguity: first-party encoder Accept negotiation

### Specification

Draft SSE binding: `Accept: text/event-stream`. Quickstart Python encoder may switch to protobuf from `Accept`.

### Question

Is protobuf required of every server?

### Possible interpretations

A. SSE-only servers are conformant.  
B. Servers SHOULD negotiate protobuf when advertised.

### Current implementation

SSE-only. `406` if `Accept` lacks `text/event-stream`.

### Interoperability impact

A client that only sends protobuf Accept will not talk to this server.

### Recommendation

Make SSE the mandatory binding; protobuf optional.
