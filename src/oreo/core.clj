(ns oreo.core
  (:require
   [aero.core :as aero]
   [clojure.walk :as walk]
   [com.stuartsierra.component :as component]))

;; Support for functions as components

(defrecord FnComponent [fun]
  clojure.lang.IFn
  (invoke [_this]
    (fun))
  (invoke [_this args]
    (fun args)))

(defrecord FnComponentWDeps [fun]
  clojure.lang.IFn
  (invoke [this]
    (fun (dissoc this :fun)))
  (invoke [this args]
    (fun (dissoc this :fun) args)))

(defmethod aero/reader 'oreo/fnc
  [_opts _tag value]
  (let [fn-component (requiring-resolve (symbol value))]
    (->FnComponent fn-component)))

(defmethod aero/reader 'oreo/fnc-w-deps
  [_opts _tag value]
  (let [fn-component (requiring-resolve (symbol value))]
    (->FnComponentWDeps fn-component)))

(defn resolve-system-from-config
  "Traverse system config, and for each map that has :oreo/create key do the following:
  - resolve the Component constructor, referenced as fully namespaced keyword (e.g. :app.component.foo/create)
  - optionally pass component configuration stored under :oreo/config key
  - optionally configure the component to wire dependencies into it, just like Component does"
  [system-config]
  (walk/postwalk (fn [thing]
                   (if (and (map? thing)
                            (:oreo/create thing))
                     (let [{:oreo/keys [config create dependencies]} thing
                           constructor' (cond
                                          (keyword? create) (requiring-resolve (symbol create))
                                          (instance? FnComponentWDeps create) (constantly create)
                                          (instance? FnComponent create) (constantly create)
                                          :else (throw (ex-info "invalid constructor" thing)))]

                       (cond-> (constructor' config)
                         dependencies (component/using dependencies)))
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
      :oreo/system
      make-system-map))
