(ns oreo.core-test
  (:require
   [oreo.core :as oreo]
   [aero.core :as aero]
   [matcher-combinators.test :refer [match?]]
   [clojure.test :refer [deftest is testing]]
   [utility-belt.component :as component.util]
   [com.stuartsierra.component :as component]))

(def system-config
  "test/oreo/test.edn")

(defprotocol IPublisher
  :extend-via-metadata true
  (publish [this message])
  (get-messages [this]))

(defrecord Publisher [store]
  IPublisher
  (publish [_this message]
    (swap! store conj message))
  (get-messages [_this]
    @store))

(defn create-publisher []
  (->Publisher (atom [])))

(defn create-dummy [init]
  (component.util/map->component {:init init
                                  :start (fn [this]
                                           (assoc this :started? true))
                                  :stop (fn [this]
                                          (assoc this :started? false))}))

(defn handler [{:keys [component]}]
  (let [{:keys [settings publisher]} component]
    (publish publisher
             (format "Greeting: %s" (-> settings :greeting)))
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body (format "Greeting: %s" (-> settings :greeting))}))

(defn http-client [url]
  (slurp url))

(deftest init-and-start-test
  (testing "a small system is created and started"
    (let [{:keys [publisher http-client dummy] :as system} (-> system-config
                                                               aero/read-config
                                                               oreo/create-system
                                                               component/start)]

      (testing "structure is there"
        (is (match? {:settings {:greeting "hello world"}
                     :dummy {:started? true :settings {:greeting "hello world"}}
                     :http-client #'oreo.core-test/http-client
                     :http-server {:config {:port 8080
                                            :join? false}
                                   :settings {:greeting "hello world"}}

                     :publisher {:store (:store publisher)}}
                    system)))

      (testing "all features tested: dependency injection, function components etc"
        (is (= "Greeting: hello world"
               (http-client "http://localhost:8080/")))

        (is (= ["Greeting: hello world"]
               (get-messages publisher)))

        (is (true? (:started? dummy))))

      (let [system-after (component/stop system)]
        (testing "system is stopped"
          (is (match? {:settings {:greeting "hello world"}
                       :dummy {:started? false :settings {:greeting "hello world"}}
                       :http-client #'oreo.core-test/http-client
                       :http-server {:config {:port 8080
                                              :join? false}
                                     :settings {:greeting "hello world"}}}
                      system-after)))))))

(deftest clj-plain-vars-test
  (testing "system defined in .clj using plain var references"
    (let [system-config {:publisher #:oc {:create create-publisher}
                         :settings {:greeting "hello from plain vars"}}
          {:keys [publisher] :as system} (-> system-config
                                             oreo/make-system-map
                                             component/start)]
      (testing "component is created and functional"
        (publish publisher "plain vars work")
        (is (= ["plain vars work"] (get-messages publisher))))

      (component/stop system))))

(deftest clj-data-readers-test
  (testing "system defined in .clj using data reader tags works the same as .edn"
    (let [system-config {:publisher #:oc {:create #oc/ref :oreo.core-test/create-publisher}
                         :settings {:greeting "hello from clj"}}
          {:keys [publisher] :as system} (-> system-config
                                             oreo/make-system-map
                                             component/start)]
      (testing "component is created and functional"
        (publish publisher "test message")
        (is (= ["test message"] (get-messages publisher))))

      (component/stop system))))

(deftest profiles-test
  (let [create-system (fn [profile]
                        (-> (aero/read-config "test/oreo/profile-test.edn" {:profile profile})
                            (oreo/create-system)
                            (component/start)
                            (select-keys [:api :db :worker])))]
    (testing "default profile"
      (is (= {:api {:db {:port 543 :started? true :uri "localhost"} :port 1000 :started? true}
              :db {:port 543 :started? true :uri "localhost"}
              :worker {:count 3 :db {:port 543 :started? true :uri "localhost"} :started? true}}
             ;; will use default profile
             (create-system nil))))

    (testing "api profile"
      (is (= {:api {:db {:port 543 :started? true :uri "localhost"} :port 1000 :started? true}
              :db {:port 543 :started? true :uri "localhost"}}
             ;; will use default profile
             (create-system :api))))

    (testing "api profile"
      (is (= {:worker {:count 3 :db {:port 543 :started? true :uri "localhost"} :started? true}
              :db {:port 543 :started? true :uri "localhost"}}
             ;; will use default profile
             (create-system :worker))))))
