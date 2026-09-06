(ns ag-ui.client.http
  "HTTP+SSE consumer. Surfaces malformed events distinctly from transport errors."
  (:require [org.httpkit.client :as http]
            [ag-ui.serialization.json :as json]
            [ag-ui.sse :as sse]
            [ag-ui.state.reduce :as reduce]))

(defn- default-input []
  {:thread-id "thread_local"
   :run-id (str "run_" (System/currentTimeMillis))
   :messages [{:id "u1" :role "user" :content "Hello"}]})

(defn- body->str
  "Normalize an HTTP body on both JVM http-kit and Babashka."
  [body]
  (cond
    (string? body) body
    (nil? body) ""
    (instance? java.io.InputStream body) (slurp body)
    (instance? java.io.Reader body) (slurp body)
    :else (str body)))

(defn run-agent
  "POST RunAgentInput to url and parse the SSE response.

  Returns:
    {:ok true :events [...] :decoded [...] :state ... :status http-status}
    {:ok false :kind :transport|:http|:malformed-json|:protocol :...}"
  [{:keys [url input headers timeout]
    :or {timeout 15000}}]
  (let [payload (json/encode-json (or input (default-input)))
        req {:url url
             :method :post
             :headers (merge {"Content-Type" "application/json"
                              "Accept" "text/event-stream"}
                             headers)
             :body payload
             :timeout timeout}
        {:keys [status body error]} @(http/request req)]
    (cond
      error
      {:ok false :kind :transport :error (str error)}

      (nil? status)
      {:ok false :kind :transport :error "no HTTP status"}

      (>= status 400)
      {:ok false :kind :http :status status :body body}

      (not (and (string? (get-in req [:headers "Accept"])) true))
      {:ok false :kind :http :status status :body body}

      :else
      (let [body (body->str body)
            decoded (sse/decode-stream body)
            failures (filterv (comp not :ok) decoded)
            events (mapv :event (filter :ok decoded))]
        (if (seq failures)
          {:ok false
           :kind :protocol
           :status status
           :decoded decoded
           :events events
           :failures failures}
          {:ok true
           :status status
           :decoded decoded
           :events events
           :state (reduce/reduce-events events input)})))))

(defn connect
  "Convenience wrapper returning just the event seq, throwing on failure."
  [url input]
  (let [r (run-agent {:url url :input input})]
    (if (:ok r)
      (:events r)
      (throw (ex-info "AG-UI client run failed" r)))))
