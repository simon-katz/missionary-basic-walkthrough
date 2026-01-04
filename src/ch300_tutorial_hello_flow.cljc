(ns ch300-tutorial-hello-flow
  (:require [missionary.core :as m]))

;; Copied from
;; https://github.com/leonoel/missionary/blob/master/doc/tutorials/hello_flow.md

;; This tutorial will help you familiarize with the •flow• abstraction.

;; A •flow• is a value representing a process able to produce an arbitrary
;; number of values before terminating. Like •task•s, they're asynchronous under
;; the hood and also support failure and graceful shutdown.

(comment

  ;; Basic operations
  ;; ================

  ;; You can build a •flow• from an arbitrary collection with `m/seed`:

  (def input ; a •flow• that produces the 10 first integers
    (m/seed (range 10)))

  ;; You can reduce a •flow• with `m/reduce`, turning it into a •task•:

  (def sum ; a •task• that produces the sum of the 10 first integers
    (m/reduce + input))

  (m/? sum) ; => 45

  ;; `m/eduction` passes a •flow• through a transducer:

  (m/? (m/reduce conj (m/eduction (partition-all 4) input)))
  ;; => [[0 1 2 3] [4 5 6 7] [8 9]]


  ;; Ambiguous evaluation
  ;; ====================

  ;; Not very interesting so far, because we haven't performed any •action• yet.

  ;; NOTE: (nomis) ^^ Huh? We've run •task•s, so we've performed •action•s,
  ;;       right? Maybe this is an edit-o.

  ;; Let's introduce the `m/ap` macro.

  ;; `m/ap` creates a •flow• from a body of forms. An `m/ap` form is called an
  ;; /ambiguous process block/.

  ;; `m/ap` is to •flow•s what `m/sp` is to •task•s. Like `m/sp`, it can be
  ;; parked with `m/?`, but it has an additional superpower: it can be *forked*.

  (def hello-world
    (m/ap
      (println (m/?> (m/seed ["Hello" "World" "!"])))
      (m/? (m/sleep 1000))))

  (m/? (m/reduce conj hello-world))
  ;; -> (straight away) Hello
  ;; -> (1000 ms later) World
  ;; -> (1000 ms later) !
  ;; => (1000 ms later) [nil nil nil]

  ;; The `m/?>` operator pulls the first seeded value, forks evaluation and
  ;; moves on until end of body, producing result `nil`, then *backtracks*
  ;; evaluation to the fork point, pulls another value, forks evaluation again,
  ;; and so on until enumeration is exhausted. Meanwhile, `m/reduce`
  ;; consolidates each result into a vector. In an `m/ap` block, expressions
  ;; have more than one possible value, that's why they're called *ambiguous
  ;; process*.


  ;; Preemptive forking
  ;; ==================

  ;; In the previous example, pulling a value from the •flow• passed to `m/?>`
  ;; transfers evaluation control to the forked process, and waits for
  ;; evaluation to be completed before pulling another value from the •flow•.
  ;; In some cases though, we want the •flow• to keep priority over the forked
  ;; process, so it can be shut down when more values become available.
  ;; That kind of forking is implemented by `m/?<`.

  ;; We can use it to implement debounce operators. A debounced •flow• is
  ;; a •flow• emitting only values that are not followed by another one within
  ;; a given delay.

  (import 'missionary.Cancelled)

  (defn debounce [delay flow]
    (m/ap (let [x (m/?< flow)]             ;; pull a value preemptively
            (try (m/? (m/sleep delay x))   ;; emit this value after given delay
                 (catch Cancelled _ (m/amb>)))))) ;; emit nothing if cancelled

  ;; To test it, we need a •flow• of values emitting at various intervals.

  (defn clock [intervals]
    (m/ap (let [i (m/?> (m/seed intervals))]
            (m/? (m/sleep i i)))))

  (m/? (->> (clock [24 79 67 34 18 9 99 37])
            (debounce 50)
            (m/reduce conj)))
  ;; => [24 79 9 37]

  ;; Nomis example:
  (m/? (->> (clock [1 2 3 4 5 6 60 7 8 9 10 60])
            (debounce 50)
            (m/reduce conj)))
  ;; => [6 10 60]


  ;; Concurrent forking
  ;; ==================

  ;; What if we want to fork the processes concurrently? Use the `m/?>` operator
  ;; with its extra `m/par` argument. It forks evaluation for `m/par` values
  ;; concurrently, *all* values if you use the value `##Inf` for `m/par`.
  ;; Values are returned from the •flow• in the order they finish, which is not
  ;; necessarily the initial order.

  (m/? (m/reduce conj (m/ap (let [ms (m/?> ##Inf (m/seed [300 100 400 200]))]
                              (m/? (m/sleep ms ms))))))
  ;; => [100 200 300 400]
  )
