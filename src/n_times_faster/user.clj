(ns n-times-faster.user
  (:require [clj-async-profiler.core :as prof]
            [clj-java-decompiler.core :refer [decompile disassemble]]
            [criterium.core :refer [bench quick-bench]]
            [n-times-faster.core :as n]))

(prof/serve-ui 8080)
(let [input (n/gen-input (* 512 512))]
  (prof/profile (dotimes [_ 1000] (n/baseline-bytevector-unrolled input))))

(decompile
 (let [asbytes (.getBytes "ssssppp")
       s-count (reduce (fn [^long c b] (unchecked-add c (bit-and b 1))) 0 asbytes)]
   (unchecked-subtract (unchecked-multiply 2 s-count) (alength asbytes))))

(let [input (n/gen-input 1e6)
      fns '(n/baseline
            n/baseline-branchless
            n/baseline-bytevector
            n/baseline-bytevector-unrolled)]

  (doseq [fn fns]
    (let [f (ns-resolve (find-ns 'n-times-faster.core)
                        (symbol (name (symbol fn))))]
      (println)
      (println (format "%s: (%d input len)" f (.length ^String input)))
      (if true
        (quick-bench (f input))
        (do
          (dotimes [_ 50] (f input))
          (println (time (f input))))))))
