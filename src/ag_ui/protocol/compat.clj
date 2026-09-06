(ns ag-ui.protocol.compat
  "Compatibility boundary: translate retired 0.x shapes before enforcement.

  Mapping from https://docs.ag-ui.com/concepts/reasoning (THINKING_* → REASONING_*).
  Translation runs before chunk expansion and lifecycle checks.")

(def thinking->reasoning
  {"THINKING_START" "REASONING_START"
   "THINKING_END" "REASONING_END"
   "THINKING_CONTENT" "REASONING_MESSAGE_CONTENT"
   "THINKING_TEXT_MESSAGE_START" "REASONING_MESSAGE_START"
   "THINKING_TEXT_MESSAGE_CONTENT" "REASONING_MESSAGE_CONTENT"
   "THINKING_TEXT_MESSAGE_END" "REASONING_MESSAGE_END"})

(defn translate-event
  "Return {:event translated :translated? bool :warning?}."
  [event]
  (let [t (:type event)
        replacement (thinking->reasoning t)]
    (if-not replacement
      {:event event :translated? false}
      (let [ev (assoc event :type replacement)
            ev (if (and (= replacement "REASONING_MESSAGE_START")
                        (not (contains? ev :role)))
                 (assoc ev :role "reasoning")
                 ev)]
        {:event ev
         :translated? true
         :warning {:code :retired-shape
                   :from t
                   :to replacement
                   :message (str t " is retired; translated to " replacement)}}))))

(defn translate-stream
  [events]
  (let [steps (mapv translate-event events)]
    {:events (mapv :event steps)
     :warnings (into [] (keep :warning steps))}))
