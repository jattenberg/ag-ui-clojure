(ns ag-ui.conformance.fixtures-test
  (:require [clojure.test :refer [deftest is]]
            [ag-ui.conformance.core :as core]))

(deftest valid-fixtures-pass
  (doseq [r (core/run-valid-fixtures)]
    (is (:ok r) (pr-str r))))

(deftest malformed-fixtures-fail
  (doseq [r (core/run-malformed-fixtures)]
    (is (:ok r) (str "should reject " (:name r) " " (pr-str r)))))
