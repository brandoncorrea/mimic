(ns bwa.mimic.manual-worker-spec
  (:require [bwa.mimic.manual-worker :as sut]
            [bwa.mimic.spec-helper :as spec-helper]
            [bwa.mimic.spec-helperc :as spec-helperc]
            [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [speclj.core :refer-macros [context describe should-be it should-be-nil should-not-contain should= should<]]))

(describe "Manual Worker"
  (spec-helperc/capture-logs-around)
  (spec-helper/with-manual-worker)

  (context "Intervals"

    (it "begins with no intervals"
      (should-be empty? (sut/intervals)))

    (it "can use clear-interval or clear-timeout interchangeably"
      (let [id-1 (sut/set-interval ccc/noop)
            id-2 (sut/set-interval ccc/noop)]
        (should= 2 (count (sut/intervals)))
        (sut/clear-interval id-1)
        (should= 1 (count (sut/intervals)))
        (sut/clear-timeout id-2)
        (should-be empty? (sut/intervals))))

    (context "creating"

      (it "returns an incrementing, non-zero ID for each new interval"
        (let [id-1 (sut/set-interval ccc/noop)
              id-2 (sut/set-interval ccc/noop)
              id-3 (sut/set-interval ccc/noop 1000)
              id-4 (sut/set-interval ccc/noop 1000 :arg-1 :arg-2)]
          (should< 0 id-1)
          (should= (inc id-1) id-2)
          (should= (inc id-2) id-3)
          (should= (inc id-3) id-4)))

      (it "caches for later use"
        (let [id (sut/set-interval ccc/noop 1000)
              [interval :as intervals] (sut/intervals)]
          (should= 1 (count intervals))
          (should= 1000 (:delay interval))
          (should= id (:id interval))
          (should= ccc/noop (:handler interval))
          (should-not-contain :args interval)
          (should-not-contain :code interval)))

      (it "warns on 0-millisecond delay"
        (let [id (sut/set-interval println)
              [interval :as intervals] (sut/intervals)]
          (should= 1 (count intervals))
          (should= 0 (:delay interval))
          (should= id (:id interval))
          (should= println (:handler interval))
          (should-not-contain :args interval)
          (should-not-contain :code interval)
          (should= "Interval created with a delay of 0. This will execute on every 'tick'." (log/captured-logs-str))))

      (it "warns on negative millisecond delays"
        (let [id (sut/set-interval println -1)
              [interval :as intervals] (sut/intervals)]
          (should= 1 (count intervals))
          (should= -1 (:delay interval))
          (should= id (:id interval))
          (should= println (:handler interval))
          (should-not-contain :args interval)
          (should-not-contain :code interval)
          (should= "Interval created with a delay of -1. This will execute on every 'tick'." (log/captured-logs-str))))

      (it "warns when provided with a string"
        (let [id (sut/set-interval "console.log('AHH!')" 1000)
              [interval] (sut/intervals)]
          (should= 1000 (:delay interval))
          (should= id (:id interval))
          (should= "console.log('AHH!')" (:code interval))
          (should-not-contain :args interval)
          (should-not-contain :handler interval)
          (should= "Interval created with a string instead of a function. eval will be executed." (log/captured-logs-str))))

      (it "creates an interval with arguments"
        (let [id (sut/set-interval ccc/noop 123 "arg-1" "arg-2")
              [interval] (sut/intervals)]
          (should= 123 (:delay interval))
          (should= id (:id interval))
          (should= ccc/noop (:handler interval))
          (should= ["arg-1" "arg-2"] (:args interval))
          (should-not-contain :code interval)))

      (it "ignores arguments when code interval created with arguments"
        (let [id (sut/set-interval "console.log('hi')" 123 "arg-1" "arg-2")
              [interval] (sut/intervals)]
          (should= 123 (:delay interval))
          (should= id (:id interval))
          (should= "console.log('hi')" (:code interval))
          (should-not-contain :handler interval)
          (should-not-contain :args interval)))

      (it "clears an interval by its id"
        (let [id (sut/set-interval ccc/noop 1000)]
          (should-be-nil (sut/clear-interval id))
          (should-be empty? (sut/intervals))))
      )

    (context "ticking"

      (it "invokes the handler of a created interval, given its id"
        (let [atm (atom 0)
              id  (sut/set-interval #(swap! atm inc) 1000)]
          (should= 0 @atm)
          (sut/tick! id)
          (should= 1 @atm)
          (sut/tick! id)
          (should= 2 @atm)))

      (it "invokes the handler of a created interval, given the interval"
        (let [atm (atom 0)
              _id (sut/set-interval #(swap! atm inc) 1000)
              [interval] (sut/intervals)]
          (should= 0 @atm)
          (sut/tick! interval)
          (should= 1 @atm)
          (sut/tick! interval)
          (should= 2 @atm)))

      (it "evaluates code for an interval"
        (let [messages (atom [])
              id       (sut/set-interval "workerTest('blah')" 100)]
          (set! js/workerTest #(swap! messages conj %))
          (should= [] @messages)
          (sut/tick! id)
          (should= ["blah"] @messages)
          (sut/tick! id)
          (should= ["blah" "blah"] @messages)))

      (it "passes args down through an interval"
        (let [atm (atom 0)
              id  (sut/set-interval (partial swap! atm +) 1000 1 2 3)]
          (should= 0 @atm)
          (sut/tick! id)
          (should= 6 @atm)
          (sut/tick! id)
          (should= 12 @atm)))

      )
    )

  (context "Timeouts"

    (it "begins with no timeouts"
      (should-be empty? (sut/timeouts)))

    (it "can use clear-interval or clear-timeout interchangeably"
      (let [id-1 (sut/set-timeout ccc/noop)
            id-2 (sut/set-timeout ccc/noop)]
        (should= 2 (count (sut/timeouts)))
        (sut/clear-timeout id-2)
        (should= 1 (count (sut/timeouts)))
        (sut/clear-interval id-1)
        (should-be empty? (sut/timeouts))))

    (context "creation"

      (it "shares the same id pool as intervals"
        (let [id-1 (sut/set-interval ccc/noop)
              id-2 (sut/set-timeout ccc/noop)
              id-3 (sut/set-interval ccc/noop 100)
              id-4 (sut/set-timeout ccc/noop 200)
              id-5 (sut/set-interval ccc/noop 100 "arg-1" "arg-2")
              id-6 (sut/set-timeout ccc/noop 200 "arg-3" "arg-4")]
          (should< 0 id-1)
          (should= (inc id-1) id-2)
          (should= (inc id-2) id-3)
          (should= (inc id-3) id-4)
          (should= (inc id-4) id-5)
          (should= (inc id-5) id-6)
          (should= 3 (count (sut/intervals)))
          (should= 3 (count (sut/timeouts)))))

      (it "caches for later use"
        (let [id (sut/set-timeout ccc/noop 1000)
              [timeout :as timeouts] (sut/timeouts)]
          (should= 1 (count timeouts))
          (should= 1000 (:delay timeout))
          (should= id (:id timeout))
          (should= ccc/noop (:handler timeout))
          (should-not-contain :args timeout)
          (should-not-contain :code timeout)))

      (it "warns on 0-millisecond delay"
        (let [id (sut/set-timeout println)
              [timeout :as timeouts] (sut/timeouts)]
          (should= 1 (count timeouts))
          (should= 0 (:delay timeout))
          (should= id (:id timeout))
          (should= println (:handler timeout))
          (should-not-contain :args timeout)
          (should-not-contain :code timeout)
          (should= "Timeout created with a delay of 0. This will execute on every 'tick'." (log/captured-logs-str))))

      (it "warns on negative millisecond delays"
        (let [id (sut/set-timeout println -1)
              [timeout :as timeouts] (sut/timeouts)]
          (should= 1 (count timeouts))
          (should= -1 (:delay timeout))
          (should= id (:id timeout))
          (should= println (:handler timeout))
          (should-not-contain :args timeout)
          (should-not-contain :code timeout)
          (should= "Timeout created with a delay of -1. This will execute on every 'tick'." (log/captured-logs-str))))

      (it "warns when provided with a string"
        (let [id (sut/set-timeout "console.log('AHH!')" 1000)
              [timeout] (sut/timeouts)]
          (should= 1000 (:delay timeout))
          (should= id (:id timeout))
          (should= "console.log('AHH!')" (:code timeout))
          (should-not-contain :args timeout)
          (should-not-contain :handler timeout)
          (should= "Timeout created with a string instead of a function. eval will be executed." (log/captured-logs-str))))

      (it "creates a timeout with arguments"
        (let [id (sut/set-timeout ccc/noop 123 "arg-1" "arg-2")
              [timeout] (sut/timeouts)]
          (should= 123 (:delay timeout))
          (should= id (:id timeout))
          (should= ccc/noop (:handler timeout))
          (should= ["arg-1" "arg-2"] (:args timeout))
          (should-not-contain :code timeout)))

      (it "ignores arguments when code timeout created with arguments"
        (let [id (sut/set-timeout "console.log('hi')" 123 "arg-1" "arg-2")
              [timeout] (sut/timeouts)]
          (should= 123 (:delay timeout))
          (should= id (:id timeout))
          (should= "console.log('hi')" (:code timeout))
          (should-not-contain :handler timeout)
          (should-not-contain :args timeout)))

      (it "clears a timeout by its id"
        (let [id (sut/set-timeout ccc/noop 1000)]
          (should-be-nil (sut/clear-timeout id))
          (should-be empty? (sut/timeouts))))

      )

    (context "ticking"

      (it "invokes the handler of a created timeout, given its id"
        (let [atm (atom 0)
              id  (sut/set-timeout #(swap! atm inc) 1000)]
          (should= 0 @atm)
          (sut/tick! id)
          (should= 1 @atm)))

      (it "invokes the handler of a created timeout, given the timeout"
        (let [atm (atom 0)
              _id (sut/set-timeout #(swap! atm inc) 1000)
              [timeout] (sut/timeouts)]
          (should= 0 @atm)
          (sut/tick! timeout)
          (should= 1 @atm)))

      (it "evaluates code for a timeout"
        (let [messages (atom [])
              id       (sut/set-timeout "workerTest('blah')" 100)]
          (set! js/workerTest #(swap! messages conj %))
          (should= [] @messages)
          (sut/tick! id)
          (should= ["blah"] @messages)))

      (it "passes args down through a timeout"
        (let [atm (atom 0)
              id  (sut/set-timeout (partial swap! atm +) 1000 1 2 3)]
          (should= 0 @atm)
          (sut/tick! id)
          (should= 6 @atm)))

      (it "removes the timeout after it has been invoked"
        (let [id (sut/set-timeout ccc/noop 1000)]
          (sut/tick! id)
          (should-be empty? (sut/timeouts))))
      )
    )

  (context "Spec Helper"

    (it "redefines js/setInterval"
      (should-be empty? (sut/timeouts))
      (should-be empty? (sut/intervals))
      (js/setInterval ccc/noop 1000)
      (should= 1 (count (sut/intervals))))

    (it "redefines js/setTimeout"
      (should-be empty? (sut/timeouts))
      (should-be empty? (sut/intervals))
      (js/setTimeout ccc/noop 1000)
      (should= 1 (count (sut/timeouts))))
    )

  )
