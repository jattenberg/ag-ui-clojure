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

(deftest golden-python-sse
  (let [body (slurp "fixtures/interop/python-ag-ui-protocol.sse")
        decoded (sse/decode-stream body)]
    (is (every? :ok decoded))
    (is (= ["RUN_STARTED" "TEXT_MESSAGE_START" "TEXT_MESSAGE_CONTENT"
            "TEXT_MESSAGE_CONTENT" "TEXT_MESSAGE_END" "RUN_FINISHED"]
           (mapv (comp :type :event) decoded)))))

(deftest malformed-data
  (let [decoded (sse/decode-stream "data: {not json}\n\n")]
    (is (not (:ok (first decoded))))
    (is (= :malformed-json (:kind (first decoded))))))

(deftest multiline-data-joins
  (let [body "data: {\"type\":\"CUSTOM\",\"name\":\"n\",\ndata: \"value\":1}\n\n"
        decoded (sse/decode-stream body)]
    (is (:ok (first decoded)))
    (is (= "CUSTOM" (get-in decoded [0 :event :type])))))

(deftest events-only-throws
  (is (thrown? Exception (sse/events-only (sse/decode-stream "data: {not json}\n\n")))))

(deftest dropped-unknown-type
  (let [body (sse/encode-event {:type "FUTURE_EVENT" :x 1})
        decoded (sse/decode-stream body)]
    (is (:dropped? (first decoded)))
    (is (= [] (sse/events-only decoded)))))
