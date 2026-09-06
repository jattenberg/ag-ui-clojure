(ns ag-ui.protocol.invariants-test
  (:require [clojure.test :refer [deftest is]]
            [ag-ui.protocol.invariants :as inv]
            [ag-ui.protocol.chunks :as chunks]
            [ag-ui.conformance.core :as core]))

(deftest run-must-start
  (is (not (:ok (inv/check-stream [{:type "TEXT_MESSAGE_START" :message-id "m"}])))))

(deftest happy-path
  (is (:ok (inv/check-stream
            [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
             {:type "TEXT_MESSAGE_START" :message-id "m" :role "assistant"}
             {:type "TEXT_MESSAGE_CONTENT" :message-id "m" :delta "hi"}
             {:type "TEXT_MESSAGE_END" :message-id "m"}
             {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}]))))

(deftest open-message-at-finish
  (let [r (inv/check-stream
           [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
            {:type "TEXT_MESSAGE_START" :message-id "m"}
            {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}])]
    (is (not (:ok r)))
    (is (= :open-at-finish (get-in r [:violation :code])))))

(deftest tool-end-without-start
  (is (not (:ok (inv/check-stream
                 [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
                  {:type "TOOL_CALL_END" :tool-call-id "c"}
                  {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}])))))

(deftest overlapping-steps-allowed
  (is (:ok (inv/check-stream
            [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
             {:type "STEP_STARTED" :step-name "a"}
             {:type "STEP_STARTED" :step-name "b"}
             {:type "STEP_FINISHED" :step-name "a"}
             {:type "STEP_FINISHED" :step-name "b"}
             {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}]))))

(deftest chunk-expansion-then-lifecycle
  (let [expanded (chunks/expand-chunks
                  [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
                   {:type "TEXT_MESSAGE_CHUNK" :message-id "m" :delta "Hi"}
                   {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}])]
    (is (:ok expanded))
    (is (:ok (inv/check-stream (:events expanded))))))
