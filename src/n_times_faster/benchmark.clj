(ns n-times-faster.benchmark
  (:require [criterium.core :refer [bench quick-bench]]
            [n-times-faster.core :refer [baseline baseline-branchless
                                                baseline-bytevector
                                                baseline-bytevector-unrolled gen-input]]))

(defn benchmark [& {:keys [quick] :or {quick true}}]

  (println "# benchmarking")
  (println "# this takes a while ...")

  (let [input (gen-input 1e6)
        fns (list #'baseline
                  #'baseline-branchless
                  #'baseline-bytevector
                  #'baseline-bytevector-unrolled)]
    (doseq [f fns]
      (println "\n# benchmarking" (-> f (meta) :name) "\n#")
      (if quick
        (quick-bench (f input))
        (bench (f input))))))
