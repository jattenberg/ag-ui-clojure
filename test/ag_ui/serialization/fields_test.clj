(ns ag-ui.serialization.fields-test
  (:require [clojure.test :refer [deftest is]]
            [ag-ui.serialization.fields :as fields]
            [ag-ui.serialization.json :as json]))

(deftest known-round-trip-keys
  (doseq [[wire k] fields/wire->clj]
    (is (= wire (fields/key->wire k)) wire)
    (is (= k (fields/key->clj wire)) wire)))

(deftest unknown-camel-kebab
  (is (= "parentSubagentRunId" (fields/key->wire :parent-subagent-run-id)))
  (is (= :not-in-schema (fields/key->clj "notInSchema"))))

(deftest extra-property-strips-at-runtime-round-trip
  (let [e {:type "TEXT_MESSAGE_CONTENT" :message-id "m" :delta "x"}
        encoded (json/encode-json e)]
    (is (re-find #"messageId" encoded))
    (is (= e (json/decode-json-strict encoded)))))
