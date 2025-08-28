(ns oreo.model-test
  (:require [oreo.model]
            [oreo.core]
            [aero.core :as aero]
            [clojure.test :refer [deftest is testing]]))

(defn create [& _] :test)

(deftest validations-happy-path-test
  (testing "happy path"
    (let [example {:anything create
                   :foo #:oc {:create create}
                   :bar #:oc {:create create
                              :using [:foo]}

                   :baz #:oc {:create create
                              :init {}
                              :using [:foo :bar]}}]

      (testing "simple validation"
        (is (= example (oreo.model/validate! example)))))))

(deftest using-specs-test
  (testing "vector"
    (is (= {:foobar #:oc {:create create :deps [:foobar]}}
           (oreo.model/validate! {:foobar #:oc {:create create :deps [:foobar]}}))))

  (testing "map"
    (is (= {:foobar #:oc {:create create :deps {:foobar :foobar}}}
           (oreo.model/validate! {:foobar #:oc {:create create :deps {:foobar :foobar}}})))))

(deftest missing-create-test
  (testing "invalid component def map"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid system definition"
                          (oreo.model/validate! {:bar #:oc {:create nil}})))))

(deftest missing-deps-test
  (testing "missing dependency checker"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid system definition"
                          (oreo.model/validate! {:foobar #:oc {:create identity
                                                               :using [:bananas]}})))))

(deftest nil-component-test
  (testing "`nil` is not valid"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid system definition"
                          (oreo.model/validate! {:foobar nil})))))

(deftest test-config-verify-test
  (testing "unit test system example"
    (is (nil? (oreo.model/any-invalid? (-> (aero/read-config "test/oreo/test.edn")
                                           :oc/system)))))

  (testing "profile test system example"
    (is (nil? (oreo.model/any-invalid? (-> (aero/read-config "test/oreo/profile-test.edn")
                                           :oc/system))))))
