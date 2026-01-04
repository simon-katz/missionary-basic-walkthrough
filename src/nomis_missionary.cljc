(ns nomis-missionary
  #?(:clj (:require
           [missionary.core :as m])))

(defn success [x] (fn [! _] (! x) #(do)))
(defn failure [x] (fn [_ !] (! x) #(do)))

(defn task-001 [x] (fn [!s _] (!s (+ 1 (* 10 x))) #(do)))
(defn task-002 [x] (fn [!s _] (!s (+ 2 (* 10 x))) #(do)))
(defn task-003 [x] (fn [!s _] (!s (+ 3 (* 10 x))) #(do)))

#?(:clj
   (defn ? [task]
     (let [!s-promise (promise)
           _cancel!   (task (fn [v] (deliver !s-promise v))
                            (fn [v]
                              ;; This is probably very wrong.
                              (throw (ex-info "Failure" {:v v}))))]
       @!s-promise)))

(comment
  #?(:clj (? (success 12)))                               ; => 12
  #?(:clj (? (task-001 (? (task-002 (? (task-003 0))))))) ; => 321
  #?(:clj (? (failure (Exception. "KO"))))                ; =>throws
  #?(:clj (? (task-001 (? (task-002 (? (failure (Exception. "KO")))))))) ; =>throws

  #?(:clj (let [[v1 v2] (? (m/join vector
                                   (m/sp "hi")
                                   (m/sp "there")))]
            (printf "Read %s from %s%n" v1 v2)))
  ;; -> Read hi from there
  ;; => nil
  )

;; Questions
;; =========

(def can-tasks-throw?
  ;; NOTE: https://github.com/leonoel/task says that a •task• must not throw.
  ;;       But there are examples that have •task•s throwing.
  ;;       Hmmmm.
  nil)

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(def ooh!-debounced-flow!
  ;; A debounced •flow• is a •flow• emitting only values that are not followed
  ;; by another one within a given delay.
  ;;
  ;; So can we use this to implement `postponed-events/c-throttle` in
  ;; `nomis-timelines`?
  ;;
  ;; What would be the right way to get a •flow• from a series of events?
  ;; - See https://github.com/leonoel/missionary/wiki/Continuous-flows#integrated-from-discrete-events
  ;; - If you can't make sense of that (or perhaps initially in any case),
  ;;   perhaps `reset!` an atom to each event and use `m/watch` to create
  ;;   a •flow•.
  nil)
