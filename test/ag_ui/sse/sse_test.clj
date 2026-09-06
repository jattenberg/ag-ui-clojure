(ns ag-ui.sse.sse-test
  (:require [clojure.test :refer [deftest is]]
            [ag-ui.sse :as sse]))

(deftest encode-decode
  (let [events [{:type "RUN_STARTED" :thread-id "t" :run-id "r"}
                 {:type "RUN_FINISHED" :thread-id "t" :run-id "r"}]
        body (sse/encode-stream events)
        decoded (sse/decode-stream body)]
    (is (re-find #"data: " body))
    (is (every? :ok decoded))
    (is (= events (mapv :event decoded)))))

(deftest ignores-comments-and-event-fields
  (let [body (str ": keep-alive\n"
                  "event: message\n"
                  "id: 1\n"
                  "data: {\"type\":\"RUN_ERROR\",\"message\":\"x\"}\n\n")
        decoded (sse/decode-stream body)]
    (is (= 1 (count decoded)))
    (is (= "RUN_ERROR" (get-in decoded [0 :event :type])))))

(deftest malformed-data
  (let [decoded (sse/decode-stream "data: {not json}\n\n")]
    (is (not (:ok (first decoded))))
    (is (= :malformed-json (:kind (first decoded))))))
