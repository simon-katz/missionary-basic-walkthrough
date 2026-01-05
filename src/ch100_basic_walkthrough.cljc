(ns ch100-basic-walkthrough
  (:require
   [missionary.core :as m]
   [nomis-missionary]))

;; Copied from
;; https://github.com/leonoel/missionary/wiki/Basic-Walkthrough:-Tasks-&-Flows

(comment

  ;; Task
  ;; ====

  ;; Skip ahead to "Nomis task examples" to see some of these examples
  ;; being run.

  ;; A Missionary •task• is a value representing an •action• to be performed.

  ;; Use various API functions to create •task•s.

  (m/sleep 800)                  ; a sleep •task•
  (m/timeout (m/sleep 1000) 800) ; a timeout •task•

  ;; Use a sequential process block to create a •task• from a body of forms.

  (m/sp (println "one") :two)

  ;; Compose •task•s, inside a process block, by running them and waiting on
  ;; their values with `m/?`.

  (m/sp (println "Let's take a nap...")
        (str (m/? (m/sleep 900 "Hi "))
             (m/? (m/sleep 100 "there!"))))

  ;; A •task• is not executed when created, it should be explicitly started.

  ;; Because a •task• has no identity the same •task• can be run an arbitrary
  ;; number of times. Each time the underlying •action• will be performed and
  ;; may produce different results.

  ;; A Missionary •task• is implemented as described at
  ;; https://github.com/leonoel/task
  ;; It is a function which can be asynchronously executed when provided with
  ;; a success continuation function and an error continuation function. It then
  ;; returns a function which can be used to cancel the •task• execution.

  ;; Pseudo-code description:
  ;;
  ;; A •task• is `(fn [success-continuation error-continuation])` which returns
  ;; `cancel-fn`
  ;;
  ;; `success-continuation` is `(fn [value] ...)`
  ;; `error-continuation`   is `(fn [value] ...)`
  ;;
  ;; `cancel-fn` is `(fn [])`
  ;; `cancel-fn` is `(fn [] ...)` ???? nomis: surely?

  ;; Asynchronously run a •task• by invoking it and use continuation functions
  ;; to process a successful or failing result.

  (let [task (m/sp "world")]
    (task #(println "Hello" %)
          #(println :KO %)))
  ;; -> Hello world
  ;; => (a cancel-fn)

  ;; Cancel an •action• using its cancel function.

  (def a-task (m/sleep 15000 :done))
  (def cancel (a-task #(println :ok %) (fn [_] (println :KO))))
  (cancel)
  ;; -> :KO
  ;; => nil

  ;; Alternatively, if the host platform supports blocking thread, a •task• can
  ;; be executed using the `m/?` function outside of any process block.

  #?(:clj (m/? (m/sp :hello))) ; => :hello

  ;; A •task• blocking on IO or taking a lot of CPU time can be created with
  ;; `m/via` to be executed on an OS thread.

  #?(:clj (m/via m/blk (Thread/sleep 1000) :done))
  #?(:clj (m/via m/cpu (+ 1 1)))


  ;; Task Examples
  ;; =============

  ;; Create two •task•s and read them values sequentially.

  #?(:clj (let [v1 (m/? (m/sp "hi"))
                v2 (m/? (m/sp "there"))]
            (printf "Read %s from %s%n" v1 v2)))
  ;; -> Read hi from there
  ;; => nil

  ;; Create two •task•s and read them values asynchronously.

  #?(:clj (let [[v1 v2] (m/? (m/join vector
                                     (m/sp "hi")
                                     (m/sp "there")))]
            (printf "Read %s from %s%n" v1 v2)))
  ;; -> Read hi from there
  ;; => nil

  ;; ---------------------------------------------------------------------------
  ;; >>>> BEGIN Nomis task examples

  ;; Let's run some of the earlier •task•s, and some others too:

  #?(:clj (m/?            (m/sleep 2000)))               ; 2000 ms delay, => nil
  #?(:clj (m/?            (m/sleep 2000 :v)))            ; 2000 ms delay, => :v
  #?(:clj (m/? (m/timeout (m/sleep 2000 :v)  4000)))     ; 2000 ms delay, => :v
  #?(:clj (m/? (m/timeout (m/sleep 2000 :v1) 4000 :v2))) ; 2000 ms delay, => :v1
  #?(:clj (m/? (m/timeout (m/sleep 2000 :v)  1000)))     ; 1000 ms delay, => nil
  #?(:clj (m/? (m/timeout (m/sleep 2000 :v1) 1000 :v2))) ; 1000 ms delay, => :v2

  #?(:clj (m/? (m/sp (println "one") :two)))
  ;; -> one
  ;; => :two

  #?(:clj (m/? (m/sp (println "Let's take a nap...")
                     (str (m/? (m/sleep 900 "Hi "))
                          (m/? (m/sleep 100 "there!"))))))
  ;; -> Let's take a nap...
  ;; => "Hi there!"

  #?(:clj (m/? (m/via m/blk (Thread/sleep 1000) :done))) ; => :done
  #?(:clj (m/? (m/via m/cpu (+ 1 1))))                   ; => 2

  ;; Simpler example of composing •task•s inside a process block:

  #?(:clj (m/? (m/sp [(m/? (m/sp :v1))
                      (m/? (m/sp :v2))])))

  ;; Some definitions from https://github.com/leonoel/task

  (defn success [x] (fn [! _] (! x) #(do)))
  (defn failure [x] (fn [_ !] (! x) #(do)))

  ;; And uses of them:

  #?(:clj (m/? (success 12))) ; => 12
  #?(:clj (m/? (failure (Exception. "KO")))) ; =>throws java.lang.Exception KO

  ;; Some •task•s of my own:

  (defn task-001 [x] (fn [!s _] (!s (+ 1 (* 10 x))) #(do)))
  (defn task-002 [x] (fn [!s _] (!s (+ 2 (* 10 x))) #(do)))
  (defn task-003 [x] (fn [!s _] (!s (+ 3 (* 10 x))) #(do)))

  ;; More-deeply nested •task•s:

  #?(:clj (m/? (task-001 0)))                                   ; => 1
  #?(:clj (m/? (task-001 (m/? (task-002 0)))))                  ; => 21
  #?(:clj (m/? (task-001 (m/? (task-002 (m/? (task-003 0))))))) ; => 321

  ;; Propagation of failures:
  #?(:clj (m/? (task-001 (m/? (task-002 (m/? (failure (Exception. "KO"))))))))
  ;; =>throws java.lang.Exception KO

  ;; <<<< END Nomis task examples
  ;; ---------------------------------------------------------------------------


  ;; Flow
  ;; ====

  ;; Skip ahead to "Nomis flow examples" to see some of these examples
  ;; being run.

  ;; A Missionary •flow• is a value representing a process able to produce an
  ;; arbitrary number of values, at any point in time, before terminating.

  ;; Use various API functions to create, compose or transform •flow•s.

  (m/seed [1 2 3])
  (m/zip vector (m/seed (range 3)) (m/seed [:a :b :c]))
  (m/eduction (map inc) (m/seed [1 2 3]))

  ;; `m/ap` creates a •flow• from a body of forms. An `m/ap` form is called an
  ;; /ambiguous process block/.

  ;; In an ambiguous process block, various functions can fork the process on
  ;; arrival of a new value from a •flow•. See documentation for `m/?>` and
  ;; `m/?<`.

  (m/ap (println (m/?> (m/seed [1 2]))))

  ;; •flow•s are not executed/consumed at creation. You must execute a •task• to
  ;; consume them. Use `m/reduce` to define a •task• from a •flow•.

  #?(:clj (let [a-flow (m/seed (range 4))
                a-task (m/reduce conj a-flow)]
            (m/? a-task)))
  ;; => [0 1 2 3]

  ;; Tip: If a •flow• generates side-effects, drain it with reduce
  ;; and (constantly nil).

  #?(:clj (m/? (m/reduce
                (constantly nil)
                (m/ap (println "Hi" (m/?> ##Inf (m/seed (range 5))))))))
  ;; -> Hi 4
  ;;    Hi 3
  ;;    Hi 2
  ;;    Hi 1
  ;;    Hi 0
  ;; => nil


  ;; Flow Examples
  ;; =============

  ;; Produce 1000 values asynchronously and read them as soon as they
  ;; are available.

  #?(:clj (let [begin (System/currentTimeMillis)
                ;; Create a •flow• of values generated by asynchronous •task•s:
                inputs (repeat 1000
                               ;; A •task• has no identity; it can be reused.
                               (m/via m/cpu "hi"))
                values (m/ap
                         (let [;; Create a •flow• of •task•s and fork on every
                               ;; •task• in parallel.
                               flow (m/seed inputs)
                               task (m/?> ##Inf flow)]
                           ;; Get a forked •task• value when available.
                           (m/? task)))
                ;; Drain the •flow• of values and count them:
                n (m/?
                   ;; The •task•s are executed and the •flow• is consumed here.
                   (m/reduce (fn [acc v]
                               (assert (= "hi" v))
                               (inc acc))
                             0 values))]
            (println "Read" n "msgs in" (- (System/currentTimeMillis) begin) "ms")))
  ;; -> Read 1000 msgs in 14 ms
  ;; => nil

  ;; ---------------------------------------------------------------------------
  ;; >>>> BEGIN Nomis flow examples

  ;; Let's run some of the earlier •flow•s:

  #?(:clj (defn flow->values [flow]
            (let [task (m/reduce conj flow)]
              (m/? task))))

  #?(:clj (flow->values (m/seed [1 2 3])))
  ;; => [1 2 3]

  #?(:clj (flow->values (m/zip vector (m/seed (range 3)) (m/seed [:a :b :c]))))
  ;; => [[0 :a] [1 :b] [2 :c]]

  #?(:clj (flow->values (m/eduction (map inc) (m/seed [1 2 3]))))
  ;; => [2 3 4]

  #?(:clj (flow->values (m/ap (println (m/?> (m/seed [1 2]))))))
  ;; -> 1
  ;;    2
  ;; => [nil nil]

  ;; And some other •flow•s:

  #?(:clj (flow->values (m/ap (println (m/?< (m/seed [1 2]))))))
  ;; -> 1
  ;;    2
  ;; => [nil nil]

  ;; <<<< END Nomis flow examples
  ;; ---------------------------------------------------------------------------
  )
