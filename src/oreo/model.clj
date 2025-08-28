(ns oreo.model
  (:require [clojure.spec.alpha :as s]
            [clojure.set :as set]))

;; NOTE: we are validating AFTER Aero resolves all refs, so this always has to be a function
(s/def :oc/create #(or (fn? %) (var? %)))

;; TODO: support extended deps format from utility-belt.component/using+ ?
(s/def :oc/using (s/or :map? (s/map-of keyword? keyword?)
                       :vec? (s/coll-of keyword? :distinct true :into [])))

;; spec for {<name> #oc{:create <qualified-symbol-or-keyword> ?:init <map> ?:using [keyword] ?}}
(s/def ::component (s/or ;; this map is a structure that describes a component
                    :map? (s/keys :req [:oc/create]
                                  :opt [:oc/init
                                        :oc/using])
                    :anything? #(not (nil? %))))
(defn any-invalid?
  [system-config]
  (->> system-config
       (vals)
       (mapv (fn [component]
               ;; XXX: we can't really do this kind of validation in spec itself, because
               ;;      a map itself can be a valid component - eg. a static value, which is a common pattern
               ;;      when people pass config values as part of the system
               (when (or (nil? component)
                         (and (map? component) (contains? component :oc/create)))
                 (let [{:oc/keys [create using]} component
                       errors (cond-> {}
                                (not (s/valid? :oc/create create))
                                (assoc :invalid-create create)

                                (and using
                                     (not (s/valid? :oc/using using)))
                                (assoc :invalid-using using))]
                   (when (not-empty errors)
                     {:component component
                      :errors errors})))))
       (remove nil?)
       seq))

(defn verify-deps [system-config]
  (let [all-keys (set (keys system-config))]
    (->> system-config
         (vals)
         (mapv (fn [component]
                 (when (and (map? component)
                            (:oc/using component))
                   (let [deps (set (get component :oc/using #{}))]
                     (when-not (set/subset? deps all-keys)
                       {:component component
                        :missing-deps (set/difference deps all-keys)})))))
         (remove nil?)
         seq)))

(defn validate!
  [system-config]
  (when-let [?invalid (or (seq (any-invalid? system-config))
                          (seq (verify-deps system-config)))]
    (throw (ex-info "Invalid system definition"
                    {:errors ?invalid})))
  system-config)
