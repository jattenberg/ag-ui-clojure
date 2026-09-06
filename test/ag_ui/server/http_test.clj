(ns ag-ui.server.http-test
  (:require [clojure.test :refer [deftest is]]
            [ag-ui.server.http :as http]
            [ag-ui.client.http :as client]))

(deftest echo-server-round-trip
  (http/start! {:port 18765})
  (try
    (let [result (client/run-agent
                  {:url "http://127.0.0.1:18765/"
                   :input {:thread-id "t"
                           :run-id "r"
                           :messages [{:id "u1" :role "user" :content "Hello"}]}})]
      (is (:ok result) (pr-str result))
      (is (= "RUN_STARTED" (get-in result [:events 0 :type])))
      (is (= "RUN_FINISHED" (get-in result [:events (dec (count (:events result))) :type])))
      (is (= "Hello, world." (get-in result [:state :messages-by-id "msg_1" :content]))))
    (finally
      (http/stop!))))

(deftest rejects-malformed-input
  (http/start! {:port 18766})
  (try
    (let [result (client/run-agent
                  {:url "http://127.0.0.1:18766/"
                   :input {:thread-id "t"}})]
      (is (not (:ok result)))
      (is (= :http (:kind result))))
    (finally
      (http/stop!))))
