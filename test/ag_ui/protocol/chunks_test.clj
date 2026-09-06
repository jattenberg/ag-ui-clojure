(ns ag-ui.protocol.chunks-test
  (:require [clojure.test :refer [deftest is testing]]
            [ag-ui.protocol.chunks :as chunks]
            [ag-ui.protocol.invariants :as inv]))

(deftest first-text-chunk-requires-id
  (let [r (chunks/expand-chunks
           [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
            {:type "TEXT_MESSAGE_CHUNK" :delta "x"}])]
    (is (not (:ok r)))
    (is (= :chunk-missing-id (get-in r [:error :code])))))

(deftest text-chunks-expand-and-flush
  (let [r (chunks/expand-chunks
           [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
            {:type "TEXT_MESSAGE_CHUNK" :message-id "m" :role "assistant" :delta "A"}
            {:type "TEXT_MESSAGE_CHUNK" :delta "B"}
            {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}])]
    (is (:ok r))
    (is (= ["RUN_STARTED" "TEXT_MESSAGE_START" "TEXT_MESSAGE_CONTENT"
            "TEXT_MESSAGE_CONTENT" "TEXT_MESSAGE_END" "RUN_FINISHED"]
           (mapv :type (:events r))))
    (is (:ok (inv/check-stream (:events r))))))

(deftest tool-chunks-expand
  (let [r (chunks/expand-chunks
           [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
            {:type "TOOL_CALL_CHUNK" :tool-call-id "c" :tool-call-name "f" :delta "{"}
            {:type "TOOL_CALL_CHUNK" :delta "}"}
            {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}])]
    (is (:ok r))
    (is (:ok (inv/check-stream (:events r))))))

(deftest reasoning-chunks-expand
  (let [r (chunks/expand-chunks
           [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
            {:type "REASONING_START" :message-id "s"}
            {:type "REASONING_MESSAGE_CHUNK" :message-id "rm" :delta "a"}
            {:type "REASONING_MESSAGE_CHUNK" :delta "b"}
            {:type "REASONING_END" :message-id "s"}
            {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}])]
    (is (:ok r))
    (is (= "reasoning" (:role (first (filter #(= "REASONING_MESSAGE_START" (:type %))
                                             (:events r))))))
    (is (:ok (inv/check-stream (:events r))))))

(deftest role-conflict-is-fatal
  (testing "TEXT_MESSAGE_CHUNK role must match opener"
    (let [r (chunks/expand-chunks
             [{:type "TEXT_MESSAGE_CHUNK" :message-id "m" :role "assistant" :delta "a"}
              {:type "TEXT_MESSAGE_CHUNK" :role "user" :delta "b"}])]
      (is (not (:ok r)))
      (is (= :chunk-conflict (get-in r [:error :code]))))))
