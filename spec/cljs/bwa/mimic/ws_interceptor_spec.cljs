(ns bwa.mimic.ws-interceptor-spec
  (:require-macros [speclj.core :refer [before context describe it redefs-around should-be-a should-have-invoked should= stub with-stubs]])
  (:require [bwa.mimic.memory-server :as mem-server]
            [bwa.mimic.server :as server]
            [bwa.mimic.ws-interceptor :as sut]
            [c3kit.wire.js :as wjs]
            [speclj.core]))

(declare server)

(describe "WebSocket Interceptor"
  (with-stubs)
  (before (reset! server/impl (mem-server/->MemoryServer)))

  (context "->WebSocketProxy"

    (it "url only"
      (let [ws (sut/->WebSocketInterceptor "ws://javascript.info")]
        (should-be-a js/WebSocket ws)
        (should= "ws://javascript.info/" (wjs/o-get ws "url"))
        (should= "" (wjs/o-get ws "protocol"))
        (should= [ws] (server/connections))))

    (it "with protocols"
      (let [ws (sut/->WebSocketInterceptor "ws://javascript.info" "foo")]
        (should-be-a js/WebSocket ws)
        (should= "ws://javascript.info/" (wjs/o-get ws "url"))
        (should= "" (wjs/o-get ws "protocol"))
        (should= [ws] (server/connections))))

    (it "with WebSocket alias"
      (let [js-web-socket js/WebSocket]
        (set! js/WebSocket sut/->WebSocketInterceptor)
        (let [ws (js/WebSocket. "ws://javascript.info")]
          (should-be-a js-web-socket ws)
          (should= [ws] (server/connections)))))

    )
  )
