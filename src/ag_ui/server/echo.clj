(ns ag-ui.server.echo
  "Deterministic fake agent. No LLM. Behaviour is a function of RunAgentInput."
  (:require [clojure.string :as str]))

(defn- last-user-text [input]
  (let [msg (->> (:messages input)
                 reverse
                 (filter #(= "user" (:role %)))
                 first)
        c (:content msg)]
    (cond
      (string? c) c
      (vector? c) (->> c (filter #(= "text" (:type %))) (map :text) (str/join ""))
      :else "")))

(defn- text-run [thread-id run-id message-id chunks]
  (concat
   [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
    {:type "TEXT_MESSAGE_START" :message-id message-id :role "assistant"}]
   (map (fn [d] {:type "TEXT_MESSAGE_CONTENT" :message-id message-id :delta d}) chunks)
   [{:type "TEXT_MESSAGE_END" :message-id message-id}
    {:type "RUN_FINISHED" :thread-id thread-id :run-id run-id}]))

(defn events-for
  "Pure: input map -> event vector."
  [input]
  (let [thread-id (or (:thread-id input) "thread_missing")
        run-id (or (:run-id input) "run_missing")
        text (last-user-text input)
        lower (str/lower-case (or text ""))]
    (cond
      (str/includes? lower "tool")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "TOOL_CALL_START" :tool-call-id "call_1" :tool-call-name "lookup" :parent-message-id "msg_tool"}
       {:type "TOOL_CALL_ARGS" :tool-call-id "call_1" :delta "{\"q\":"}
       {:type "TOOL_CALL_ARGS" :tool-call-id "call_1" :delta "\"clojure\"}"}
       {:type "TOOL_CALL_END" :tool-call-id "call_1"}
       {:type "TOOL_CALL_RESULT" :message-id "msg_result" :tool-call-id "call_1" :content "ok"}
       {:type "RUN_FINISHED" :thread-id thread-id :run-id run-id}]

      (str/includes? lower "state")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "STATE_SNAPSHOT" :snapshot {:count 0 :label "n"}}
       {:type "STATE_DELTA" :delta [{:op "replace" :path "/count" :value 1}]}
       {:type "RUN_FINISHED" :thread-id thread-id :run-id run-id}]

      (str/includes? lower "interrupt")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "STATE_SNAPSHOT" :snapshot {:awaiting true}}
       {:type "MESSAGES_SNAPSHOT" :messages (vec (:messages input))}
       {:type "RUN_FINISHED"
        :thread-id thread-id
        :run-id run-id
        :outcome {:type "interrupt"
                  :interrupts [{:id "int_1"
                                :reason "confirmation"
                                :message "Proceed?"}]}}]

      (str/includes? lower "error")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "RUN_ERROR" :message "deterministic failure" :code "echo.error"}]

      (str/includes? lower "custom")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "CUSTOM" :name "ag-ui-clojure.ping" :value {:ok true}}
       {:type "RUN_FINISHED" :thread-id thread-id :run-id run-id}]

      :else
      (text-run thread-id run-id "msg_1" ["Hello" ", world."]))))
