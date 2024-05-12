# Mimic

A library consisting of fake implementations of hard-to-reach things.

> **Shapechanger.** The [mimic](https://www.dndbeyond.com/monsters/16957-mimic) can use its action to polymorph into an object or back into its true, amorphous form. Its statistics are the same in each form. Any equipment it is wearing or carrying isn't transformed. It reverts to its true form if it dies.

## Installation

Note: MVN deployment coming soon

Add the following dependency to your deps.edn file:

    dev.bwawan/mimic {:git/url "git@github.com:brandoncorrea/mimic.git" :git/sha "GIT_SHA"}

## Examples

### WebSockets

#### In Your Tests (speclj)

```clojure
(ns acme.mimic
  (:require [c3kit.wire.js :as wjs]))

(def connected? (atom false))
(def messages (atom []))
(defn connect! []
  (let [ws (js/WebSocket. "ws://localhost:8080")]
    (wjs/add-listener ws "open" #(reset! connected? true))
    (wjs/add-listener ws "close" #(reset! connected? false))
    (wjs/add-listener ws "message" #(swap! messages conj (wjs/o-get % "data")))))
```

```clojure
(ns acme.mimic-spec
  (:require [bwa.mimic.server :as server]
            [bwa.mimic.spec-helper :as mimic-helper]
            [c3kit.wire.js :as wjs]
            [speclj.core :refer-macros [describe it should= should-not should-be should]]))

(describe "Mimic"
  (mimic-helper/with-memory-websockets)

  (it "listens for messages"
    (should-be empty? (server/connections))
    (connect!)
    (let [[ws :as connections] (server/connections)]
      (should= 1 (count connections))
      (should= "ws://localhost:8080/" (wjs/o-get ws "url"))

      (should-not @connected?)
      (server/open ws)
      (should @connected?)

      (server/send ws "first payload")
      (should= ["first payload"] @messages)
      (server/send ws "second payload")
      (should= ["first payload" "second payload"] @messages)

      (server/close ws)
      (should-not @connected?)))
  )
```

#### In Your App

```clojure
(ns acme.main
  (:require [bwa.mimic.memory-server :as mem-server]
            [bwa.mimic.memory-websocket :as mem-ws]
            [bwa.mimic.server :as server]))

(defn -main []
  ; redefine the WebSocket constructor
  (set! js/WebSocket mem-ws/->MemSocket)

  ; set your browser's repl options
  (set! js/Server server/repl-options)

  ; Configure your server implementation
  (reset! server/impl (mem-server/->MemServer))
  )
```
