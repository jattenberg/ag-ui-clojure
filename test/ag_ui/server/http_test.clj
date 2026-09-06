(ns ag-ui.server.http-test
  (:require [clojure.test :refer [deftest is]]
            [org.httpkit.client :as hk]
            [ag-ui.server.http :as http]
            [ag-ui.client.http :as client]
            [ag-ui.serialization.json :as json]))

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

(deftest health-and-missing-accept
  (http/start! {:port 18767})
  (try
    (is (= 200 (:status @(hk/get "http://127.0.0.1:18767/health"))))
    (let [r @(hk/post "http://127.0.0.1:18767/"
                      {:headers {"content-type" "application/json"}
                       :body (json/encode-json {:thread-id "t" :run-id "r" :messages []})})]
      (is (= 406 (:status r))))
    (finally
      (http/stop!))))

(deftest echo-activity-keyword
  (http/start! {:port 18768})
  (try
    (let [result (client/run-agent
                  {:url "http://127.0.0.1:18768/"
                   :input {:thread-id "t" :run-id "r"
                           :messages [{:id "u1" :role "user" :content "activity"}]}})]
      (is (:ok result) (pr-str result))
      (is (some #(= "ACTIVITY_SNAPSHOT" (:type %)) (:events result))))
    (finally
      (http/stop!))))
