(ns ag-ui.conformance.cli-test
  (:require [clojure.test :refer [deftest is]]
            [ag-ui.conformance.cli :as cli]
            [ag-ui.conformance.core :as core]))

(deftest parse-profile-and-endpoint
  (is (= :all (:profile (cli/parse-args []))))
  (is (= :producer (:profile (cli/parse-args ["--profile" "producer"]))))
  (is (= "http://127.0.0.1:9/"
         (:endpoint (cli/parse-args ["--endpoint" "http://127.0.0.1:9/"]))))
  (is (= "http://x/" (:endpoint (cli/parse-args ["http://x/"]))))
  (is (= {:profile :consumer :endpoint "http://e/"}
         (select-keys (cli/parse-args ["--profile" "consumer" "--endpoint" "http://e/"])
                      [:profile :endpoint]))))

(deftest future-event-split
  (let [by-name #(into {} (map (juxt :name identity) %))
        prod (by-name (core/run-profile :producer))
        cons (by-name (core/run-profile :consumer))]
    (is (= "reject" (get-in prod ["future-event" :expected])))
    (is (true? (get-in prod ["future-event" :ok])))
    (is (= "accept" (get-in cons ["future-event" :expected])))
    (is (true? (get-in cons ["future-event" :ok])))
    (is (true? (get-in cons ["unknown-event-type" :skipped])))))
