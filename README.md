# N times faster in clojure

This was a **clojure** exercise in optimization inspired by the blog post [{n} times faster than C, where n = 128](https://ipthomas.com/blog/2023/07/n-times-faster-than-c-where-n-128/) by Thomas Ip and the accompaning [rust code](https://github.com/tommyip/n_times_faster_than_c).

However it feature-creeped to learning about

* clojure project setup
* decompiling ([clj-java-decompiler](https://github.com/clojure-goes-fast/clj-java-decompiler)) and profiling ([clj-async-profiler](https://github.com/clojure-goes-fast/clj-async-profiler))
* macros, specifically for unrolling loops
* java Vector API ([`jdk.incubator.vector.ByteVector`](https://docs.oracle.com/en/java/javase/20/docs/api/jdk.incubator.vector/jdk/incubator/vector/ByteVector.html))
* vscode/[calva](https://github.com/BetterThanTomorrow/calva) (IDE)


Matching performance was not a goal. Here are some numbers for input string length 1M.

| fn                            | time          |
| ----------------------------- | ------------- |
| baseline                      |  16 ms        |
| baseline-bytevector-unrolled  | 0.4 ms        |


_AMD Ryzen 7 3700x, java20, 256bit vector, 32 x 1 byte_

## Usage

```shell
$ clojure -X:run
$ clojure -X:test
$ clojure -X:benchmark # takes a while
```

## Head-Scratchers

* `deps.edn`, why no `:jvm-opts` on root ? had to put `--add-modules jdk.incubator.vector` into every profile
* vscode, can't resize hover widget (tooltip). really ? - [coming soon](https://github.com/microsoft/vscode/pull/185540)
* for years, I've yet to see a clojure ide/editor which displays `javadoc`.
Working with `ByteVector` was essentially browsing [oracles' docs](https://docs.oracle.com/en/java/javase/20/docs/api/jdk.incubator.vector/jdk/incubator/vector/package-summary.html).

## License

This is free and unencumbered software released into the public domain.
