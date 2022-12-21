(ns foobar.core
  (:require
    [com.stuartsierra.component :as component]
    [foobar.system :as system]))


(defn -main
  [& _args]
  (component/start (system/create))
  #_(component/stop sys))
