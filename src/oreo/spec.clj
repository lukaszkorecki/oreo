(ns oreo.spec
  (:require [clojure.spec.alpha :as s]))

(s/def :oc/create fn?)
(s/def :oc/init map?)
(s/def :oc/using (s/coll-of keyword? :distinct true :into []))

;; spec for {<name> #oc{:create <qualified-symbol-or-keyword> ?:init <map> ?:using [keyword] ?}}
(s/def ::component (s/or :anything? boolean
                         :map? (s/keys :opt [:oc/create
                                             :oc/init
                                             :oc/using])))

(defn any-invalid?
  [system-config]
  (->> system-config
       (vals)
       (mapv (fn [component]
               (when-not (s/valid? ::component component)
                 {:component component
                  :errors (s/explain-data ::component component)})))
       (remove nil?)
       seq))

(defn validate!
  [system-config]
  (when-let [?invalid (seq (any-invalid? system-config))]
    (throw (ex-info "Invalid system config, see :errors key for details"
                    {:errors ?invalid})))
  system-config)
