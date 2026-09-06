(ns ag-ui.protocol.validate-test
  (:require [clojure.test :refer [deftest is testing]]
            [ag-ui.protocol.validate :as v]
            [ag-ui.events :as events]))

(deftest validate-event-ok
  (is (:ok (events/validate-event {:type "RUN_STARTED" :thread-id "t" :run-id "r"})))
  (is (:ok (v/validate-event {:type "TEXT_MESSAGE_CONTENT" :message-id "m" :delta ""})))
  (is (:ok (v/validate-event {:type "CUSTOM" :name "x" :value nil}))))

(deftest validate-event-missing
  (let [r (v/validate-event {:type "TEXT_MESSAGE_CONTENT" :delta "x"})]
    (is (not (:ok r)))
    (is (some #(= :missing-required (:code %)) (:errors r)))))

(deftest validate-event-wrong-type
  (let [r (v/validate-event {:type "TEXT_MESSAGE_CONTENT" :message-id 1 :delta "x"})]
    (is (not (:ok r)))
    (is (some #(= :invalid-field-type (:code %)) (:errors r)))))

(deftest validate-null-optional
  (let [r (v/validate-event {:type "RUN_STARTED" :thread-id "t" :run-id "r" :parent-run-id nil})]
    (is (not (:ok r)))
    (is (some #(= :null-not-allowed (:code %)) (:errors r)))))

(deftest unknown-type-authoring-vs-runtime
  (let [e {:type "FUTURE_EVENT" :thread-id "t"}]
    (is (not (:ok (v/validate-event e {:mode :authoring}))))
    (let [rt (v/validate-event e {:mode :runtime})]
      (is (:ok rt))
      (is (:dropped? rt)))))

(deftest run-input
  (is (:ok (v/validate-run-input {:thread-id "t" :run-id "r" :messages []})))
  (is (not (:ok (v/validate-run-input {:thread-id "t" :messages []})))))
