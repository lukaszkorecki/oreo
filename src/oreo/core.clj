(ns oreo.core
  (:require
   [aero.core :as aero]
                                        ;   [clojure.walk :as walk]
   [com.stuartsierra.component :as component]
   [oreo.model]))

;; Simplfies loading functions in configs, before we event get to the system/component
;; assembly point

;; Aero reader for functions, which forces resolving the var itself
(defmethod aero/reader 'oc/ref!
  [_opts _tag value]
  (try
    (deref (requiring-resolve (symbol value)))
    (catch Exception e
      (throw (ex-info (str "Failed to resolve var: " value) {:tag _tag
                                                             :value value}
                      e)))))

;; Similar to above, but returns a var
(defmethod aero/reader 'oc/ref
  [_opts _tag value]
  (try
    (requiring-resolve (symbol value))
    (catch Exception e
      (throw (ex-info (str "Failed to resolve var: " value) {:tag _tag
                                                             :value value}
                      e)))))

(defn resolve-system-from-config
  "Traverse system config, and for each map that has :oreo/create key do the following:
  - resolve the Component constructor, referenced as fully namespaced keyword (e.g. :app.component.foo/create)
  - optionally pass component configuration stored under :oreo/config key
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
  (-> system-config
      resolve-system-from-config
      component/map->SystemMap))

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
