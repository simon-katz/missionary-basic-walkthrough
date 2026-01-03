(ns ch200-tutorial-hello-task
  (:require [missionary.core :as m]))

;; Copied from
;; https://github.com/leonoel/missionary/blob/master/doc/tutorials/hello_task.md

;; This tutorial will help you to familiarize with the •task• abstraction, the
;; simpler of missionary's core abstractions. The other one is •flow•, and will
;; be the topic of the next tutorial. It's especially important to build a solid
;; mental model about it if you're coming from an imperative background.

;; A •task• is a value representing an •action• to be performed. The •action•
;; eventually terminates with a status (success or failure) and a result.
;; A pending •action• can be cancelled at any time, making it gracefully
;; shutdown and terminate. A •task• can be run an arbitrary number of times.
;; Each time the underlying •action• will be performed, which may produce
;; different results.

;; If threads were cheap and available everywhere, we could have represented
;; •task•s as zero-argument functions (aka thunks). Instead, we chose a purely
;; asynchronous representation, providing efficiency and reach. You can think of
;; •task•s as asynchronous thunks, if you will. Just keep in mind that
;; asynchrony is a technical detail of the internal protocol, you don't need to
;; understand it to use it.

(comment

  ;; Hello World
  ;; ===========

  ;; Your main tool to work with •task•s is the `m/sp` macro. It takes a body of
  ;; clojure forms and wraps it in a •task•. The •action• performed by this
  ;; •task• is the sequential evaluation of these forms. `m/sp` stands for
  ;; /sequential process/.

  (def hello-world
    (m/sp (println "Hello world !")))

  ;; We defined the •task• `hello-world`, its •action• is to spit a message on
  ;; stdout and complete with nil.

  ;; To run a •task• we pass it to `m/?`, which performs the •task•'s •action•
  ;; and returns its result.

  (m/? hello-world)
  ;; -> Hello world !
  ;; => nil

  ;; Note: It won't work in a ClojureScript REPL because it requires to block
  ;; calling thread (more on that later).


  ;; Sequential composition
  ;; ======================

  ;; Let's look at another •task• operator: `m/sleep`. The •task•s it creates
  ;; perform the •action• of doing nothing for a given amount of milliseconds.

  (def nap (m/sleep 1000))
  (m/? nap)
  ;; => nil ; after 1 second

  ;; We can use `m/?` in `m/sp`, allowing us to sequentially compose •task•s.

  (def slowmo-hello-world
    (m/sp (println "Hello")
          (m/? nap)
          (println "World")
          (m/? nap)
          (println "!")))

  ;; NOTE: Note the interspersing of •task•s and regular Clojure code in
  ;;       `slowmo-hello-world`.

  (m/? slowmo-hello-world)
  ;; -> Hello
  ;; -> World
  ;; -> !
  ;; => nil ; after 2 seconds

  ;; Note: While the semantics of `m/?` are the same inside and outside `m/sp`
  ;; blocks (run a •task• and return its result), the mechanism is slightly
  ;; different: the `m/sp` macro will detect `m/?` calls in its body and rewrite
  ;; code in asynchronous style, such that no thread will be blocked performing
  ;; `nap` (we say the process is *parked* instead of *blocked*). Consequently,
  ;; this definition of `slowmo-hello-world` is perfectly legal in
  ;; ClojureScript. The top level `m/?` call is still not allowed though.
  ;; Use something like `(js/Promise. slowmo-hello-world)` instead.


  ;; Parallel composition
  ;; ====================

  ;; Let's introduce concurrency. `m/join` is used to run multiple •task•s
  ;; concurrently and merge results with an arbitrary function.

  (def chatty-hello-world
    (m/join vector slowmo-hello-world slowmo-hello-world))

  (m/? chatty-hello-world)
  ;; -> Hello
  ;; -> Hello
  ;; -> World
  ;; -> World
  ;; -> !
  ;; -> !
  ;; => [nil nil] ; after 2 seconds

  ;; Everything went well this time, because we performed local •action•s, but
  ;; if we were performing actual IO, we would have significant odds to
  ;; encounter failures and we need to be prepared for it.

  (def unreliable-hello-world
    (m/sp (println "Hello")
          (m/? (m/sleep 500))
          (throw (ex-info "Something went wrong." {}))))

  (m/? unreliable-hello-world)
  ;; -> Hello
  ;; =>throws after 500 ms

  (def unreliable-chatty-hello-world
    (m/join vector slowmo-hello-world unreliable-hello-world))

  (m/? unreliable-chatty-hello-world)
  ;; -> Hello
  ;; -> Hello
  ;; =>throws after 500 ms

  ;; What happened here is both •task•s have been run concurrently, each has
  ;; printed Hello, then the second •task• failed after 500 ms, then error was
  ;; propagated to the join •task•, making it fail as well. Modeling concurrency
  ;; in functional style gave us supervision for free, we don't have to program
  ;; defensively, operators do what you expect by default.

  ;; In fact, under the hood, `m/join` reacted to the error by *cancelling* the
  ;; other •action•, allowing it to gracefully shutdown before rethrowing the
  ;; error. The cancelling signal was propagated to the `m/sleep` •action•,
  ;; which deregistered itself from the scheduler and failed. Then, the sleep
  ;; failure has been rethrown in the sequential process, terminating it.
  ;; Only then, `m/join` propagated the first error.

  ;; What that means is if we want to ensure some •action• is done before
  ;; terminating, we can use `try`/`finally` just as we do with threads.

  (def finally-hello-world
    (m/sp
      (try (println "Hello")
           (m/? nap)
           (println "World")
           (m/? nap)
           (finally
             (println "!")))))

  (m/? (m/join vector unreliable-hello-world finally-hello-world))
  ;; -> Hello
  ;; -> Hello
  ;; -> !
  ;; =>throws after 500 ms


  ;; Execution model
  ;; ===============

  ;; All operators run user-provided code immediately, in the thread allowing
  ;; the computation. This scheduling strategy is the simplest possible, makes
  ;; evaluation order largely deterministic with virtually no overhead, while
  ;; leaving a lot of flexibility to the user for fine-grained
  ;; performance tuning.

  ;; One important consequence of this design decision is that user-provided
  ;; code is assumed to be inexpensive. On the JVM, blocking APIs are common,
  ;; which can lead to surprising behaviors if improperly handled.

  ;; What happens if we have a service outside of our control that blocks
  ;; our thread?

  #?(:clj (defn blocking-hello-world []
            (println "Hello")
            (Thread/sleep 500)
            (println "World")))
  (time (m/? (m/join vector (m/sp (blocking-hello-world)) (m/sp (blocking-hello-world)))))
  ;; -> Hello
  ;; -> World
  ;; -> Hello
  ;; -> World
  ;; -> "Elapsed time: 1006.127854 msecs"
  ;; => [nil nil]

  ;; Since `blocking-hello-world` is blocking the whole thread we're stuck.
  ;; For these purposes missionary allows offloading a •task• on a different
  ;; `java.util.concurrent.Executor` via the `m/via` macro. Missionary ships
  ;; with 2 predefined executors, `m/cpu` for CPU bound •task•s and `m/blk` for
  ;; IO bound (BLocKing) •task•s. With this new insight we can fix our previous
  ;; example:

  (time (m/? (m/join vector (m/via m/blk (blocking-hello-world)) (m/via m/blk (blocking-hello-world)))))
  ;; -> HelloHello
  ;; ->
  ;; -> World
  ;; -> World
  ;; -> "Elapsed time: 501.968621 msecs"
  ;; => [nil nil]
  )
