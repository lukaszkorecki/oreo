(ns foobar.system
  (:require
   [aero.core :as aero]
   [oreo.core :as oreo]
   [mokujin.log :as log]
   [mokujin.logback :as lb]
   [clojure.java.io :as io]))

(lb/configure! {:config ::lb/text
                :logger-filters {"org.eclipse" "WARN"}})

(def store
  (atom {:counter 0}))

(defn tracer []
  (log/info "api call detected"))

(def config
  (aero/read-config (io/resource "config.edn")))

(defn create
  []
  (oreo/create-system config))
