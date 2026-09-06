(ns ag-ui.server.http
  (:require [org.httpkit.server :as http]
            [ag-ui.serialization.json :as json]
            [ag-ui.protocol.validate :as validate]
            [ag-ui.server.echo :as echo]
            [ag-ui.sse :as sse]
            [ag-ui.stream :as stream]))

(defonce !server (atom nil))

(defn- json-error [status body]
  {:status status
   :headers {"Content-Type" "application/json"}
   :body (json/encode-json body)})

(defn handle-run
  [request]
  (let [accept (or (get-in request [:headers "accept"]) "")
        body (slurp (or (:body request) ""))]
    (if-not (re-find #"text/event-stream" accept)
      (json-error 406 {:error "Accept must include text/event-stream"})
      (try
        (let [decoded (json/decode-json-strict body)
              v (validate/validate-run-input decoded {:mode :runtime})]
          (if-not (:ok v)
            (json-error 400 {:error "malformed RunAgentInput" :details (:errors v)})
            (let [events (vec (stream/event-seq (echo/events-for (:input v))))
                  payload (sse/encode-stream events)]
              {:status 200
               :headers {"Content-Type" "text/event-stream"
                         "Cache-Control" "no-cache"
                         "Connection" "keep-alive"}
               :body payload})))
        (catch Exception e
          (json-error 400 {:error "malformed JSON" :message (.getMessage e)}))))))

(defn app [request]
  (let [uri (:uri request)
        method (:request-method request)]
    (cond
      (and (= :get method) (= "/health" uri))
      {:status 200 :headers {"Content-Type" "text/plain"} :body "ok"}

      (and (= :post method) (or (= "/" uri) (= "/agent" uri)))
      (handle-run request)

      :else
      {:status 404 :headers {"Content-Type" "text/plain"} :body "not found"})))

(defn start!
  ([] (start! {:port 8000}))
  ([{:keys [port] :or {port 8000}}]
   (when-let [s @!server] (s))
   (reset! !server (http/run-server app {:port port :join? false}))
   {:port port}))

(defn stop! []
  (when-let [s @!server]
    (s)
    (reset! !server nil)))
