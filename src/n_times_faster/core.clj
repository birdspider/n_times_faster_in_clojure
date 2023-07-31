(ns n-times-faster.core
  (:require [clojure.walk :refer [postwalk]]
            [kixi.stats.distribution :refer [bernoulli sample]])
  (:import [java.util Arrays]
           [jdk.incubator.vector
            ByteVector
            Vector
            VectorOperators
            VectorSpecies]))

(set! *warn-on-reflection* true)

(def ^:private ^:const UNROLL_FACTOR 32)

(defmacro unrolled-thread-first
  "`(n init expr)` => `(-> init expr expr ... n times)`

   Occurences of `:n` in `expr` will be replaced with the current iteration index.
   `n` must be known at compile-time."
  [n init expr]
  (let [expr# (if (list? expr) expr (list expr))]
    `(~'-> ~init
           ~@(map (fn [i] (postwalk #(if (= % :n) i %) expr#))
                  (range 0 (eval n))))))

(defn gen-input
  [size]
  {:pre [(pos? size)]}
  (->> (sample size (bernoulli {:p 0.5}))
       (map #(if % \s \p))
       (char-array)
       (String/copyValueOf)))

(defn unsigned-add-vec-lanes
  "sums lanes of a vector

   Since `Vector<Byte>.reduceLanes(op)` returns `Byte`, `Byte` is often too small
   to hold the result of i.e. `VectorOperators/ADD`.
   Therefore here is a `.toArray` based wrapper."
  [^Vector bv]
  (let [^bytes va (.toArray bv)]
    (areduce va i ret 0 ; loop and sum over java byte-array
             (unchecked-add ret (Byte/toUnsignedLong (aget va i))))))

(defn baseline
  "increments when \\s, decrements when \\p and returns counter"
  [^String str]
  (reduce (fn [n c] (+ n (if (= \s c) 1 -1))) 0 str))

(defn- baseline-branchless-bytes
  [^bytes bx]
  (let [s-count (reduce (fn [c b] (unchecked-add c (bit-and b 1))) 0 bx)]
    (unchecked-subtract (unchecked-multiply 2 s-count) (alength bx))))

(defn baseline-branchless
  "increments when \\s, decrements when \\p and returns counter"
  [^String str]
  (baseline-branchless-bytes (.getBytes str "ascii")))

(defn baseline-bytevector
  "increments when \\s, decrements when \\p and returns counter"
  [^String str]
  (let [b (.getBytes str "ascii")
        SPECIES ByteVector/SPECIES_PREFERRED
        SPECIES_LENGTH (.length SPECIES)
        [chunks rest] ((juxt quot rem) (alength b) SPECIES_LENGTH)
        b-tail (Arrays/copyOfRange b (unchecked-subtract-int (alength b) rest) (alength b))
        b114 (byte 114)
        b-1 (byte -1)]
    (->> (range 0 chunks)
         (reduce
          (fn [c index]
            (unchecked-add
             c
             (-> (ByteVector/fromArray SPECIES b (unchecked-multiply index SPECIES_LENGTH))
                 (.sub b114)
                 (.max b-1)
                 (.reduceLanes VectorOperators/ADD))))
          0)
         (+ (baseline-branchless-bytes b-tail)))))

(.zero ByteVector/SPECIES_PREFERRED)

(defn baseline-bytevector-unrolled
  "increments when \\s, decrements when \\p and returns counter"
  [^String str & {:keys [^VectorSpecies species] :or {species ByteVector/SPECIES_PREFERRED}}]
  (let [lanes (.length species)
        workgroup-size (* lanes UNROLL_FACTOR)
        b (.getBytes str "ascii")
        b-len (alength b)
        b-max (- (.loopBound species b-len) (rem (.loopBound species b-len) workgroup-size))
        b-tail (Arrays/copyOfRange b b-max b-len)
        b-chunks (quot b-max workgroup-size)
        vec-of-1 (.broadcast (.zero species) (byte 1))]

    #_(println b-len (String. b-tail) b-chunks (baseline-branchless-bytes b-tail))

    (+ (if (pos? b-chunks)
         (->> (range 0 b-chunks)
              (map (fn [^long chunk-index]

                     #_(println (+ 0 (* chunk-index workgroup-size)) ".." (+ -1 (* UNROLL_FACTOR lanes) (* chunk-index workgroup-size)))

                     (unrolled-thread-first
                      UNROLL_FACTOR
                      (.zero species) ; HINT: keyword :n gen'd by macro - loop index
                      (.add (.lanewise (ByteVector/fromArray species b
                                                             (unchecked-add (unchecked-multiply :n lanes)
                                                                            (unchecked-multiply chunk-index workgroup-size)))
                                       VectorOperators/AND
                                       vec-of-1)))))
              (reduce (fn [c v] (+ c (unsigned-add-vec-lanes v))) 0)
              (* 2)
              (#(- % b-max)))
         0)
       (baseline-branchless-bytes b-tail))))

; exec

(defn run [& args]
  (let [input (gen-input 1e6)
        fns '(baseline
              baseline-branchless
              baseline-bytevector
              baseline-bytevector-unrolled)]
    (doseq [fn fns]
      (println (format "%s: (%d input len)" fn (.length ^String input)))
      (let [f (ns-resolve (find-ns 'n-times-faster.core) (symbol fn))]
        (dotimes [_ 50] (f input))
        (println (time (f input)))))))

