(ns foobar.api)

(defn handler
  "Ring handler for the Jetty server - dependencies are injected under `component` key."
  [{:keys [component]}]
  (let [{:keys [tracer store]} component]
    (tracer)

    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (format "task run %s times"
                   (-> store deref :counter))}))
