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
  )
