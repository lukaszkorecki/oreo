(ns oreo.spec-test
  (:require [oreo.spec]
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
      (is (= example (oreo.spec/validate! example))))))

(deftest test-config-verify-test
  (testing "unit test system example"
    (is (nil? (oreo.spec/any-invalid? (-> "test/oreo/test.edn"
                                          (aero/read-config)
                                          :oc/system))))))
