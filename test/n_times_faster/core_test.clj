(ns n-times-faster.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.string :as str]
            [n-times-faster.core
             :refer [baseline baseline-branchless baseline-bytevector baseline-bytevector-unrolled]]))

(deftest baseline-test
  (testing "Context of the test assertions"
    (is (= 0 (baseline "")))
    (is (= -1 (baseline "p")))
    (is (=  1 (baseline "s")))
    (is (= 0 (baseline "ps")))))

(def baseline-prop
  (prop/for-all [s (gen/fmap str/join (gen/vector (gen/elements [\s \p])))]
                (= (baseline s)
                   (->> ((juxt #(get % \s 0) #(- (get % \p 0))) (frequencies s))
                        (apply +)))))

(def baseline-branchless-prop
  (prop/for-all [s (gen/fmap str/join (gen/vector (gen/elements [\s \p])))]
                (= (baseline s) (baseline-branchless s))))

(def baseline-bytevector-prop
  (prop/for-all [s (gen/fmap str/join (gen/vector (gen/elements [\s \p])))]
                (= (baseline s) (baseline-bytevector s))))

(def baseline-bytevector-unrolled-prop
  (prop/for-all [s (gen/fmap str/join (gen/vector (gen/elements [\s \p])))]
                (= (baseline s) (baseline-bytevector-unrolled s))))

(def baseline-bytevector-unrolled-large-prop
  (prop/for-all [s (gen/fmap str/join (gen/vector (gen/elements [\s \p]) 0 1e6))]
                (= (baseline-branchless s) (baseline-bytevector-unrolled s))))

(deftest baseline-check-test
  (testing "Context of the test assertions"
    (let [results [(tc/quick-check 100 baseline-prop)
                   (tc/quick-check 100 baseline-branchless-prop)
                   (tc/quick-check 100 baseline-bytevector-prop)
                   (tc/quick-check 100 baseline-bytevector-unrolled-prop)
                   (tc/quick-check 10 baseline-bytevector-unrolled-large-prop)]]
      (doseq [r results]
        (is (true? (:pass? r)) [(:fail r)])))))



