(ns ag-ui.serialization.json-test
  (:require [clojure.test :refer [deftest is]]
            [ag-ui.serialization.json :as json]))

(deftest camelcase-round-trip
  (let [e {:type "RUN_STARTED" :thread-id "thr-1" :run-id "run-1"}
        s (json/encode-json e)]
    (is (re-find #"threadId" s))
    (is (not (re-find #"thread-id" s)))
    (is (= e (json/decode-json-strict s)))))

(deftest preserve-null-in-custom-value
  (let [e {:type "CUSTOM" :name "n" :value nil}]
    (is (= e (json/decode-json-strict (json/encode-json e))))))

(deftest omit-not-null
  (let [e {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}
        m (json/decode-json-strict (json/encode-json e))]
    (is (not (contains? m :result)))
    (is (not (contains? m :outcome)))))

(deftest malformed-json
  (is (thrown? Exception (json/decode-json "{nope"))))

(deftest snapshot-string-keys
  (let [e {:type "STATE_SNAPSHOT" :snapshot {:draft {:title "x"}}}
        decoded (json/decode-json-strict (json/encode-json e))]
    (is (= "x" (get-in decoded [:snapshot "draft" "title"])))))
