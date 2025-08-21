(ns oreo.core
  (:require
   [aero.core :as aero]
   [clojure.walk :as walk]
   [com.stuartsierra.component :as component]))

;; Simplfies loading functions in configs, before we event get to the system/component
;; assembly point

;; Aero reader for functions, which forces resolving the var itself
(defmethod aero/reader 'oc/ref!
  [_opts _tag value]
  @(requiring-resolve (symbol value)))


;; Similar to above, but returns a var
(defmethod aero/reader 'oc/ref
  [_opts _tag value]
  (requiring-resolve (symbol value)))

(defn resolve-system-from-config
  "Traverse system config, and for each map that has :oreo/create key do the following:
  - resolve the Component constructor, referenced as fully namespaced keyword (e.g. :app.component.foo/create)
  - optionally pass component configuration stored under :oreo/config key
  - optionally configure the component to wire dependencies into it, just like Component does"
  [system-config]
  (walk/postwalk (fn [thing]
                                      (if (and (map? thing)
                            (:oc/create thing))
                     (let [{:oc/keys [init create using]} thing
                           component-create-fn' (if (or (symbol? create) (keyword? create))
                                                  (requiring-resolve (symbol create))
                                                  (throw (ex-info "invalid component create function" thing)))]
                       ;; FIXME: handle component init functions which do not accept config!
                       (cond-> (component-create-fn' init)
                         (seq using) (component/using using)))
                     thing))
                 system-config))

(defn make-system-map
  "Process system config, resolve all components and return component/SystemMap instance"
  [system-config]
  (-> system-config
      resolve-system-from-config
      component/map->SystemMap))

(defn create-system
  "Given a config map processed by aero.core/read-config
  creates a Component system map, the system config is expected to be
  stored under :oreo/system key"
  [config]
  (-> config
      :oc/system
      make-system-map))
