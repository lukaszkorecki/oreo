(ns foobar.core
  (:require
   [mokujin.log :as log]
   [com.stuartsierra.component :as component]
   [utility-belt.lifecycle :as lifecycle]
   [foobar.system :as system]))

(defn -main
  [& _args]
  (let [system (component/start (system/create))]
    (lifecycle/add-shutdown-hook :system-shutdown
                                 #(component/stop system))

    (log/infof "Run `curl http://localhost:%s` to test that the system is working"
               (-> system/config :api :server :port))))
