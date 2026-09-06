(ns ag-ui.conformance.fixtures-test
  (:require [clojure.test :refer [deftest is testing]]
            [ag-ui.conformance.core :as core]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [ag-ui.state.reduce :as reduce]
            [ag-ui.protocol.invariants :as inv]
            [ag-ui.protocol.chunks :as chunks]
            [ag-ui.serialization.json :as json]))

(deftest valid-fixtures-pass
  (doseq [r (core/run-valid-fixtures)]
    (is (:ok r) (pr-str r))))

(deftest malformed-fixtures-fail
  (doseq [r (core/run-malformed-fixtures)]
    (is (:ok r) (str "should reject " (:name r) " " (pr-str r)))))

(def gen-text-run
  (gen/let [chunks (gen/vector gen/string-alphanumeric 0 5)]
    (concat
     [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
      {:type "TEXT_MESSAGE_START" :message-id "m" :role "assistant"}]
     (map (fn [d] {:type "TEXT_MESSAGE_CONTENT" :message-id "m" :delta d}) chunks)
     [{:type "TEXT_MESSAGE_END" :message-id "m"}
      {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}])))

(defspec generated-text-runs-are-valid 40
  (prop/for-all [events gen-text-run]
    (let [expanded (chunks/expand-chunks events)
          life (inv/check-stream (:events expanded))
          state (reduce/reduce-events events)
          round (mapv #(json/decode-json-strict (json/encode-json %)) events)]
      (and (:ok expanded)
           (:ok life)
           (= :finished (:status state))
           (= events round)
           (= (apply str (keep :delta (filter #(= "TEXT_MESSAGE_CONTENT" (:type %)) events)))
              (get-in state [:messages-by-id "m" :content]))))))
