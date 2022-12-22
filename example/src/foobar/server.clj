(ns foobar.server
  (:require
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [foobar.widget :as widget])
  (:import
   (java.util.concurrent.atomic
    AtomicBoolean)))

(defrecord FakeServer
  [;; config
   port
   ;; dependency
   widget
   now
   ;; inner state
   logger
   check]

  component/Lifecycle

  (start
    [this]
    (let [check (AtomicBoolean. true)
          logger (future
                   (while (.get check)
                     (log/infof "SERVER serving on port %s widget: %s now: %s"
                                port
                                (widget/report widget)
                                (now))
                     (Thread/sleep 5000)))]
      (assoc this :logger logger :check check)))

  (stop
    [this]
    (.set check false)
    (future-cancel logger)
    (assoc this :check nil :logger nil)))

(defn create
  [{:keys [port]}]
  (assert (number? port) "port is not a number!")
  (map->FakeServer {:port port}))
