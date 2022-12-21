(ns oreo.core-test
  (:require
   [oreo.core :as oreo]
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [com.stuartsierra.component :as component]))

(defprotocol Inspectable
  (inspect [_this] "see whats up"))

(defrecord ComponentOne [config]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  Inspectable
  (inspect [_t] {:self :component-one :config config}))

(defrecord ComponentTwo [config one three]
  Inspectable
  (inspect [_t] {:self :component-two
                 :dependencies {:one (inspect one)
                                :three (three)}}))

(defn make-two [config]
  (->ComponentTwo config nil nil))

(defn component-three []
  {:self :component-three})

(defn component-four [{:keys [two] :as _deps} args]
  {:two (inspect two)
   :args args})

(def system-config
  "test/oreo/test.edn")

(deftest load-and-start
  (let [system (-> system-config
                   aero/read-config
                   oreo/create-system
                   component/start)]

    (testing "simple component"
      (is (= {:config {:port 5000} :self :component-one}
             (inspect (:one system)))))

    (testing "component with dependencies"

      (is (= {:dependencies {:one {:config {:port 5000} :self :component-one}
                             :three {:self :component-three}}
              :self :component-two}
             (inspect (:two system)))))

    (testing "function compoenent (no deps)"
      (is (= {:self :component-three}
             ((:three system)))))

    (testing "function component with deps"
      (is (= {:args :test
              :two {:dependencies {:one {:config {:port 5000} :self :component-one}
                                   :three {:self :component-three}}
                    :self :component-two}}
             ((:four system) :test))))))
