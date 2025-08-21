(ns foobar.scheduler
  (:require [mokujin.log :as log]))

(defn task-counter [{:keys [store]}]
  (let [res (swap! store (fn [s]
                           (update s :counter inc)))]
    (log/infof "tick %s" res)))
