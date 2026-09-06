(ns ag-ui.sse.core
  "Namespace alias matching the src/ag_ui/sse/ layout."
  (:require [ag-ui.sse :as sse]))

(def encode-event sse/encode-event)
(def encode-stream sse/encode-stream)
(def decode-stream sse/decode-stream)
(def events-only sse/events-only)
