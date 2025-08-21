(ns oreo.core-test
  (:require
   [oreo.core :as oreo]
   [aero.core :as aero]
   [matcher-combinators.test :refer [match?]]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [utility-belt.component :as component.util]
   [com.stuartsierra.component :as component]))

(def system-config
  "test/oreo/test.edn")

(defn handler [{:keys [component]}]
  (let [{:keys [settings]} component]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body (format "Greeting: %s" (-> settings :greeting))}))

(defn http-client [url]
  (slurp url))

(deftest init-and-start-test
  (testing "a small system is created and started"
    (let [{:keys [http-client] :as system} (-> system-config
                                               aero/read-config
                                               oreo/create-system
                                               component/start)]

      (testing "structure is there"
        (is (match? {:settings {:greeting "hello world"}
                     :http-client #'oreo.core-test/http-client
                     :http-server {:config {:port 8080
                                            :join? false}
                                   :settings {:greeting "hello world"}}}
                    system)))

      (testing "all features tested: dependency injection, function components etc"
        (is (= "Greeting: hello world"
               (http-client "http://localhost:8080/"))))
      (component/stop system))))
