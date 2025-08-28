(ns oreo.model-test
  (:require [oreo.model]
            [oreo.core]
            [aero.core :as aero]
            [clojure.test :refer [deftest is testing]]))

(defn create [& _] :test)

(deftest test-config-verify-test
  (let [example {:anything clojure.core/identity
                 :foo #:oc {:create create}
                 :bar #:oc {:create create
                            :using [:foo]}

                 :baz #:oc {:create create
                            :init {}
                            :using [:foo :bar]}}]

    (testing "simple validation"
      (is (= example (oreo.model/validate! example))))))

(deftest test-config-verify-test
  (testing "unit test system example"
    (is (nil? (oreo.model/any-invalid? (-> "test/oreo/test.edn"
                                           (aero/read-config)
                                           :oc/system))))))
