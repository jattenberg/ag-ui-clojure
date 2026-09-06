(ns ag-ui.state.reduce-test
  (:require [clojure.test :refer [deftest is]]
            [ag-ui.state.reduce :as reduce]
            [ag-ui.state.json-patch :as patch]))

(deftest text-accumulation
  (let [s (reduce/reduce-events
           [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
            {:type "TEXT_MESSAGE_START" :message-id "m" :role "assistant"}
            {:type "TEXT_MESSAGE_CONTENT" :message-id "m" :delta "Hello"}
            {:type "TEXT_MESSAGE_CONTENT" :message-id "m" :delta ", world."}
            {:type "TEXT_MESSAGE_END" :message-id "m"}
            {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}])]
    (is (= :finished (:status s)))
    (is (= "Hello, world." (get-in s [:messages-by-id "m" :content])))))

(deftest tool-args-accumulate
  (let [s (reduce/reduce-events
           [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
            {:type "TOOL_CALL_START" :tool-call-id "c" :tool-call-name "search" :parent-message-id "m"}
            {:type "TOOL_CALL_ARGS" :tool-call-id "c" :delta "{\"a\":"}
            {:type "TOOL_CALL_ARGS" :tool-call-id "c" :delta "1}"}
            {:type "TOOL_CALL_END" :tool-call-id "c"}
            {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}])]
    (is (= "{\"a\":1}" (get-in s [:messages-by-id "m" :tool-calls 0 :function :arguments])))))

(deftest state-snapshot-and-delta
  (let [s (reduce/reduce-events
           [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
            {:type "STATE_SNAPSHOT" :snapshot {"draft" {"title" "" "items" []}}}
            {:type "STATE_DELTA" :delta [{:op "replace" :path "/draft/title" :value "Notes"}]}
            {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}])]
    (is (= "Notes" (get-in s [:agent-state "draft" "title"])))))

(deftest failed-patch-keeps-prior
  (let [s (reduce/reduce-events
           [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
            {:type "STATE_SNAPSHOT" :snapshot {"n" 1}}
            {:type "STATE_DELTA" :delta [{:op "replace" :path "/missing" :value 2}]}
            {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}])]
    (is (= 1 (get-in s [:agent-state "n"])))
    (is (seq (:warnings s)))))

(deftest interrupt-outcome
  (let [s (reduce/reduce-events
           [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
            {:type "RUN_FINISHED" :thread-id "t" :run-id "r"
             :outcome {:type "interrupt" :interrupts [{:id "i" :reason "confirmation"}]}}])]
    (is (= "interrupt" (get-in s [:outcome :type])))
    (is (= "i" (get-in s [:interrupts 0 :id])))))

(deftest json-patch-add-append
  (is (= {:ok true :doc {"a" [1 2]}}
         (patch/apply-patch {"a" [1]} [{:op "add" :path "/a/-" :value 2}]))))
