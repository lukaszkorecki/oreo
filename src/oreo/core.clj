(ns oreo.core
  (:require
   [aero.core :as aero]
   [com.stuartsierra.component :as component]
   [oreo.model]))

;; Reader functions for resolving vars from namespaced keywords/symbols.
;; Used by both Aero reader methods (for .edn configs) and
;; Clojure data readers (for .clj system definitions via data_readers.clj).

(defn resolve-deref
  "Resolve a namespaced symbol or keyword to a var and dereference it.
  Returns the value the var points to (e.g. a function, atom, map)."
  [value]
  (try
    (deref (requiring-resolve (symbol value)))
    (catch Exception e
      (throw (ex-info (str "Failed to resolve var: " value)
                      {:tag 'oc/deref :value value}
                      e)))))

(defn resolve-ref
  "Resolve a namespaced symbol or keyword to a var without dereferencing.
  Returns the var itself."
  [value]
  (try
    (requiring-resolve (symbol value))
    (catch Exception e
      (throw (ex-info (str "Failed to resolve var: " value)
                      {:tag 'oc/ref :value value}
                      e)))))

(defmethod aero/reader 'oc/deref [_ _ value] (resolve-deref value))
(defmethod aero/reader 'oc/ref [_ _ value] (resolve-ref value))

(defn resolve-system-from-config
  "Traverse system config, and for each map that has :oc/create key do the following:
  - resolve the Component constructor, referenced as fully namespaced keyword (e.g. :app.component.foo/create)
  - optionally pass component configuration stored under :oc/init key
  - optionally configure the component to wire dependencies into it, just like Component does"
  [system-config]
  (->> system-config
       (mapcat (fn [[name thing]]
                 (if (and (map? thing)
                          (:oc/create thing))
                   (let [{:oc/keys [init create using]} thing
                         component (cond-> (if (contains? thing :oc/init)
                                             (create init)
                                             (create))
                                           (seq using) (component/using using))]
                     {name component})
                   {name thing})))))

(defn make-system-map
  "Process system config, resolve all components and return component/SystemMap instance"
  [system-config]
  (->> system-config
       resolve-system-from-config
       (component/map->SystemMap)))

(defn create-system
  "Given a config map processed by aero.core/read-config
  creates a Component system map, the system config is expected to be
  stored under `:oc/system` key by default"
  ([config]
   (create-system config :oc/system))
  ([config system-def-key]
   (-> (get config system-def-key)
       oreo.model/validate!
       make-system-map)))
