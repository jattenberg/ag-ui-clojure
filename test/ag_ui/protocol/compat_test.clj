(ns ag-ui.protocol.compat-test
  (:require [clojure.test :refer [deftest is]]
            [ag-ui.protocol.compat :as compat]
            [ag-ui.protocol.invariants :as inv]
            [ag-ui.protocol.resume :as resume]
            [ag-ui.state.reduce :as reduce]))

(deftest thinking-translates-to-reasoning
  (let [{:keys [events warnings]}
        (compat/translate-stream
         [{:type "THINKING_START" :message-id "s"}
          {:type "THINKING_TEXT_MESSAGE_START" :message-id "m"}
          {:type "THINKING_END" :message-id "s"}])]
    (is (= "REASONING_START" (:type (first events))))
    (is (= "reasoning" (:role (second events))))
    (is (seq warnings))))

(deftest reasoning-span-must-close
  (is (not (:ok (inv/check-stream
                 [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
                  {:type "REASONING_START" :message-id "s"}
                  {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}])))))

(deftest subagent-must-close
  (is (not (:ok (inv/check-stream
                 [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
                  {:type "SUBAGENT_STARTED" :subagent-run-id "sa" :name "x"}
                  {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}])))))

(deftest resume-must-cover-all
  (is (not (:ok (resume/check-resume
                 [{:id "a"} {:id "b"}]
                 [{:interrupt-id "a" :status "resolved"}]))))
  (is (:ok (resume/check-resume
            [{:id "a"}]
            [{:interrupt-id "a" :status "cancelled"}]))))

(deftest activity-lands-in-messages
  (let [s (reduce/reduce-events
           [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
            {:type "ACTIVITY_SNAPSHOT" :message-id "a" :activity-type "p" :content {"pct" 0}}
            {:type "ACTIVITY_DELTA" :message-id "a" :activity-type "p"
             :patch [{:op "replace" :path "/pct" :value 1}]}
            {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}])]
    (is (= 1 (get-in s [:messages-by-id "a" :content "pct"])))))

(deftest thinking-stream-reduces
  (let [s (reduce/reduce-events
           [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
            {:type "THINKING_START" :message-id "s"}
            {:type "THINKING_TEXT_MESSAGE_START" :message-id "m"}
            {:type "THINKING_TEXT_MESSAGE_CONTENT" :message-id "m" :delta "hi"}
            {:type "THINKING_TEXT_MESSAGE_END" :message-id "m"}
            {:type "THINKING_END" :message-id "s"}
            {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}])]
    (is (= :finished (:status s)))
    (is (= "hi" (get-in s [:messages-by-id "m" :content])))))
