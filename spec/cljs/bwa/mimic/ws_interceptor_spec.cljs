(ns bwa.mimic.ws-interceptor-spec
  (:require-macros [speclj.core :refer [after before context describe it should-be-a should-have-invoked should= stub with with-stubs]])
  (:require [bwa.mimic.event :as event]
            [bwa.mimic.memory-server :as mem-server]
            [bwa.mimic.server :as server]
            [bwa.mimic.spec-helper :as spec-helper]
            [bwa.mimic.websocket :as ws]
            [bwa.mimic.ws-interceptor :as sut]
            [c3kit.wire.js :as wjs]
            [speclj.core]))

(declare sock)

(describe "WebSocket Interceptor"
  (with-stubs)
  (before (reset! server/impl (mem-server/->MemServer)))
  (after (run! ws/close! (server/connections)))

  (context "->WebSocketInterceptor"

    (it "url only"
      (let [ws (sut/->WebSocketInterceptor "ws://localhost:8080")]
        (should-be-a js/WebSocket ws)
        (should= "ws://localhost:8080/" (wjs/o-get ws "url"))
        (should= "" (wjs/o-get ws "protocol"))
        (should= [ws] (server/connections))))

    (it "with protocols"
      (let [ws (sut/->WebSocketInterceptor "ws://localhost:8080" "foo")]
        (should-be-a js/WebSocket ws)
        (should= "ws://localhost:8080/" (wjs/o-get ws "url"))
        (should= "" (wjs/o-get ws "protocol"))
        (should= [ws] (server/connections))))

    (it "with WebSocket alias"
      (let [js-web-socket js/WebSocket]
        (set! js/WebSocket sut/->WebSocketInterceptor)
        (let [ws (js/WebSocket. "ws://localhost:8080")]
          (should-be-a js-web-socket ws)
          (should= [ws] (server/connections)))))

    (context "events"
      (spec-helper/with-websocket-impl sut/->WebSocketInterceptor)

      (with sock (js/WebSocket. "ws://localhost:8080"))

      (it "message"
        (let [event (event/->MessageEvent @sock "blah")]
          (wjs/add-listener @sock "message" (stub :listener))
          (ws/dispatch-event! @sock event)
          (should-have-invoked :listener {:with [event]})
          (should= "message" (wjs/o-get event "type"))
          (should= "blah" (wjs/o-get event "data"))
          (should= "ws://localhost:8080" (wjs/o-get event "origin"))
          (should= "" (wjs/o-get event "lastEventId"))
          ;; TODO [BAC]: ports should= ?
          (should= [] (wjs/o-get event "ports"))))

      (it "close"
        (let [event (event/->CloseEvent @sock 1006 "just because" true)]
          (wjs/add-listener @sock "close" (stub :listener))
          (ws/dispatch-event! @sock event)
          (should-have-invoked :listener {:with [event]})
          (should= "close" (wjs/o-get event "type"))
          (should= 1006 (wjs/o-get event "code"))
          (should= "just because" (wjs/o-get event "reason"))
          (should= true (wjs/o-get event "wasClean"))))

      (it "open"
        (let [event (event/->OpenEvent @sock)]
          (wjs/add-listener @sock "open" (stub :listener))
          (ws/dispatch-event! @sock event)
          (should-have-invoked :listener {:with [event]})
          (should= "open" (wjs/o-get event "type"))))

      (it "error"
        (let [event (event/->ErrorEvent @sock)]
          (wjs/add-listener @sock "error" (stub :listener))
          (ws/dispatch-event! @sock event)
          (should-have-invoked :listener {:with [event]})
          (should= "error" (wjs/o-get event "type"))))
      )
    )
  )
