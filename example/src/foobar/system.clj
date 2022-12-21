(ns foobar.system
  (:require
    [aero.core :as aero]
    [oreo.core :as oreo]
    [clojure.java.io :as io]))


(defn now
  []
  (str (java.time.LocalDateTime/now)))


(defn create
  []
  (-> "config.edn"
      io/resource
      aero/read-config
      oreo/create-system))
