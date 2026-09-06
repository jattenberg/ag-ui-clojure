(ns ag-ui.protocol.resume-test
  (:require [clojure.test :refer [deftest is]]
            [ag-ui.protocol.resume :as resume]
            [ag-ui.protocol.invariants :as inv]))

(deftest empty-open-empty-resume
  (is (:ok (resume/check-resume [] [])))
  (is (:ok (resume/check-resume [] nil))))

(deftest resume-without-open-interrupts
  (let [r (resume/check-resume [] [{:interrupt-id "x" :status "resolved"}])]
    (is (not (:ok r)))
    (is (= :resume-without-interrupt (:code r)))))

(deftest resume-required-when-pending
  (let [r (resume/check-resume [{:id "a"}] nil)]
    (is (not (:ok r)))
    (is (= :resume-required (:code r)))))

(deftest unknown-resume-id
  (let [r (resume/check-resume [{:id "a"}]
                               [{:interrupt-id "a" :status "resolved"}
                                {:interrupt-id "z" :status "resolved"}])]
    (is (not (:ok r)))
    (is (= :unknown-resume (:code r)))))

(deftest interrupts-from-finished
  (is (nil? (resume/interrupts-from-finished {:type "RUN_FINISHED" :outcome {:type "success"}})))
  (is (= [{:id "i"}]
         (resume/interrupts-from-finished
          {:type "RUN_FINISHED" :outcome {:type "interrupt" :interrupts [{:id "i"}]}}))))

(deftest stream-partial-resume-fails
  (is (not (:ok (inv/check-stream
                 [{:type "RUN_STARTED" :thread-id "t" :run-id "r1"}
                  {:type "RUN_FINISHED" :thread-id "t" :run-id "r1"
                   :outcome {:type "interrupt" :interrupts [{:id "a" :reason "x"}
                                                            {:id "b" :reason "x"}]}}
                  {:type "RUN_STARTED" :thread-id "t" :run-id "r2"
                   :input {:thread-id "t" :run-id "r2" :messages []
                           :resume [{:interrupt-id "a" :status "resolved"}]}}])))))
