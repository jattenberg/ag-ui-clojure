#!/usr/bin/env node
/**
 * POST RunAgentInput to an AG-UI SSE server and print parsed `data:` JSON.
 * Usage: node interop/node/post-echo.mjs http://127.0.0.1:8000/
 */
const url = process.argv[2] || "http://127.0.0.1:8000/";

const body = JSON.stringify({
  threadId: "interop-node",
  runId: "interop-run",
  messages: [{ id: "u1", role: "user", content: "Hello" }],
});

const res = await fetch(url, {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    Accept: "text/event-stream",
  },
  body,
});

if (!res.ok) {
  console.error("HTTP", res.status, await res.text());
  process.exit(1);
}

const text = await res.text();
const types = [];
for (const block of text.split("\n\n")) {
  const line = block.split("\n").find((l) => l.startsWith("data:"));
  if (!line) continue;
  const json = line.replace(/^data:\s?/, "");
  const event = JSON.parse(json);
  types.push(event.type);
  console.log(JSON.stringify(event));
}

const expected = [
  "RUN_STARTED",
  "TEXT_MESSAGE_START",
  "TEXT_MESSAGE_CONTENT",
  "TEXT_MESSAGE_CONTENT",
  "TEXT_MESSAGE_END",
  "RUN_FINISHED",
];
if (JSON.stringify(types) !== JSON.stringify(expected)) {
  console.error("unexpected types", types);
  process.exit(1);
}
