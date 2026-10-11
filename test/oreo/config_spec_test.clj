(ns oreo.config-spec-test
  (:require [aero.core :as aero]
            [clojure.test :refer [deftest is testing]]
            [oreo.config-spec :as config]
            [oreo.core]))

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
        (is (= example (config/validate! example)))))))

(deftest using-specs-test
  (testing "vector"
    (is (= {:foobar #:oc {:create create :using [:foobar]}}
           (config/validate! {:foobar #:oc {:create create :using [:foobar]}}))))

  (testing "map"
    (is (= {:foobar #:oc {:create create :using {:foobar :foobar}}}
           (config/validate! {:foobar #:oc {:create create :using {:foobar :foobar}}})))))

(deftest missing-create-test
  (testing "invalid component def map"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid system definition"
                          (config/validate! {:bar #:oc {:create nil}})))))

(deftest missing-deps-test
  (testing "missing dependency checker"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid system definition"
                          (config/validate! {:foobar #:oc {:create identity
                                                           :using [:bananas]}})))))

(deftest nil-component-test
  (testing "`nil` is not valid"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid system definition"
                          (config/validate! {:foobar nil})))))

(deftest test-config-verify-test
  (testing "unit test system example"
    (is (nil? (config/any-invalid? (-> (aero/read-config "test/oreo/test.edn")
                                       :oc/system)))))

  (testing "profile test system example"
    (is (nil? (config/any-invalid? (-> (aero/read-config "test/oreo/profile-test.edn")
                                       :oc/system))))))
