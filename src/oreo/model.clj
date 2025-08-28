(ns oreo.model
  (:require [clojure.spec.alpha :as s]))

;; NOTE: we are validating AFTER Aero resolves all refs, so this always has to be a function
(s/def :oc/create #(or (fn? %) (var? %)))

;; XXX - this doesn't feel that great, we don't control how components are created, I can see how `nil` is a valid init value
(s/def :oc/init (complement nil?))
(s/def :oc/using (s/coll-of keyword? :distinct true :into []))

;; spec for {<name> #oc{:create <qualified-symbol-or-keyword> ?:init <map> ?:using [keyword] ?}}
(s/def ::component (s/or :anything? boolean ; protect against `nil` and `true`/`false`
                         ;; this map is a structure that describes a component
                         :map? (s/keys :req [:oc/create]
                                       :opt [:oc/init
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
