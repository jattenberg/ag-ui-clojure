(ns ag-ui.events
  "Public event helpers. Events are plain maps:

    {:type \"RUN_STARTED\" :thread-id \"t\" :run-id \"r\"}"
  (:require [ag-ui.protocol.events :as events]
            [ag-ui.protocol.validate :as validate]))

(def event-types events/event-types)

(defn validate-event
  ([event] (validate/validate-event event))
  ([event opts] (validate/validate-event event opts)))
