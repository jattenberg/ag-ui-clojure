(ns ag-ui.server.echo-test
  (:require [clojure.test :refer [deftest is]]
            [ag-ui.server.echo :as echo]
            [ag-ui.protocol.invariants :as inv]
            [ag-ui.protocol.chunks :as chunks]
            [ag-ui.state.reduce :as reduce]))

(defn- types [input]
  (mapv :type (echo/events-for input)))

(defn- input [text]
  {:thread-id "t" :run-id "r" :messages [{:id "u1" :role "user" :content text}]})

(deftest default-hello
  (is (= ["RUN_STARTED" "TEXT_MESSAGE_START" "TEXT_MESSAGE_CONTENT"
          "TEXT_MESSAGE_CONTENT" "TEXT_MESSAGE_END" "RUN_FINISHED"]
         (types (input "Hello")))))

(deftest keyword-streams-are-legal
  (doseq [text ["tool" "state" "interrupt" "error" "custom"
                "activity" "subagent" "encrypted" "reasoning" "snapshot" "chunk"]]
    (let [events (echo/events-for (input text))
          expanded (chunks/expand-chunks events)
          reduced (reduce/reduce-events events)]
      (is (:ok expanded) text)
      (is (:ok (inv/check-stream (:events expanded))) text)
      (is (not= :protocol-error (:status reduced)) text))))

(deftest resume-input-succeeds
  (let [events (echo/events-for
                {:thread-id "t" :run-id "r2"
                 :messages [{:id "u1" :role "user" :content "go"}]
                 :resume [{:interrupt-id "int_1" :status "resolved"}]})]
    (is (= "success" (get-in (last events) [:outcome :type])))))
