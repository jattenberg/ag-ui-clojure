(ns ag-ui.serialization.json-properties-test
  "JVM-only property tests (clojure.test.check)."
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [ag-ui.serialization.json :as json]
            [ag-ui.protocol.validate :as v]))

(def gen-run-started
  (gen/hash-map
   :type (gen/return "RUN_STARTED")
   :thread-id gen/string-alphanumeric
   :run-id gen/string-alphanumeric))

(defspec encode-decode-preserves-run-started 50
  (prop/for-all [e gen-run-started]
    (let [decoded (json/decode-json-strict (json/encode-json e))]
      (and (= e decoded)
           (:ok (v/validate-event decoded))))))
