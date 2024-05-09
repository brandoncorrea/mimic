(ns bwa.mimic.memory-server-spec
  (:require-macros [speclj.core :refer [before context describe it should should-contain should-have-invoked should-not-have-invoked should-throw should= stub with with-stubs]])
  (:require [bwa.mimic.memory-server :as sut]
            [bwa.mimic.memory-websocket :as mem-socket]
            [bwa.mimic.server :as server]
            [bwa.mimic.websocket :as ws]
            [c3kit.apron.corec :as ccc]
            [c3kit.wire.js :as wjs]
            [speclj.core]
            [speclj.stub :as stub]))

(def server server/impl)
(declare sock)

(defn ->add-event-listener [sock]
  (fn [event handler]
    (let [event    (str "on" event)
          existing (wjs/o-get sock event)]
      (wjs/o-set sock event (juxt existing handler)))))

(defn ->Socket [ready-state]
  (let [sock (js-obj "readyState" ready-state
                     "onclose" ccc/noop)]
    (wjs/o-set sock "addEventListener" (->add-event-listener sock))
    sock))

(describe "Memory Server"
  (with-stubs)
  (before (wjs/o-set js/performance "now" (fn [] 123.4567))
          (reset! server/impl (sut/->MemoryServer)))

  (with sock (mem-socket/->MemSocket "ws://example.com"))

  (it "connections"
    (should= [@sock] (server/connections))
    (let [sock-2 (mem-socket/->MemSocket "ws://blah.com")]
      (should= [@sock sock-2] (server/connections))
      (ws/close! @sock)
      (should= [sock-2] (server/connections))
      (ws/close! sock-2)
      (should= [] (server/connections))))

  (context "initiate"

    (it "throws when socket is already open"
      (should-throw js/Error "Socket is not CONNECTING"
        (server/initiate (->Socket 1))))

    (it "throws when socket is closing"
      (should-throw js/Error "Socket is not CONNECTING"
        (server/initiate (->Socket 2))))

    (it "throws when socket is closing"
      (should-throw js/Error "Socket is not CONNECTING"
        (server/initiate (->Socket 2))))

    (it "stores socket in-memory"
      (let [socket (->Socket 0)]
        (server/initiate socket)
        (should-contain socket @(:sockets @server/impl))))

    (it "throws when socket has already been initiated"
      (let [socket (->Socket 0)]
        (server/initiate socket)
        (should-throw js/Error "Socket has already been initiated" (server/initiate socket))))
    )

  (context "open"

    (it "throws when already open"
      (wjs/o-set @sock "readyState" 1)
      (should-throw js/Error "Socket is not CONNECTING" (server/open @sock)))

    (it "throws when closing"
      (wjs/o-set @sock "readyState" 2)
      (should-throw js/Error "Socket is not CONNECTING" (server/open @sock)))

    (it "throws when already closed"
      (wjs/o-set @sock "readyState" 3)
      (should-throw js/Error "Socket is not CONNECTING" (server/open @sock)))

    (it "opens a websocket"
      (wjs/add-listener @sock "open" (comp (stub :open) js->clj))
      (should (ws/connecting? @sock))
      (server/open @sock)
      (should (ws/open? @sock))
      (let [[event] (stub/last-invocation-of :open)
            clj-sock (js->clj @sock)]
        (should= true (get event "isTrusted"))
        (should= false (get event "bubbles"))
        (should= false (get event "cancelBubble"))
        (should= false (get event "cancelable"))
        (should= false (get event "composed"))
        (should= clj-sock (get event "currentTarget"))
        (should= false (get event "defaultPrevented"))
        (should= 0 (get event "eventPhase"))
        (should= true (get event "returnValue"))
        (should= clj-sock (get event "srcElement"))
        (should= clj-sock (get event "target"))
        (should= 123.4567 (get event "timeStamp"))
        (should= "open" (get event "type"))))

    (it "references socket object in open event"
      (wjs/add-listener @sock "open" (stub :open))
      (server/open @sock)
      (let [[event] (stub/last-invocation-of :open)]
        (should= @sock (wjs/o-get event "currentTarget"))
        (should= @sock (wjs/o-get event "srcElement"))
        (should= @sock (wjs/o-get event "target"))))

    )

  (context "close"

    (it "throws if client is already closed"
      (wjs/o-set @sock "readyState" 3)
      (should-throw js/Error "Socket is not CONNECTING or OPEN" (server/close @sock)))

    (it "throws if client is in the process of closing"
      (wjs/o-set @sock "readyState" 2)
      (should-throw js/Error "Socket is not CONNECTING or OPEN" (server/close @sock)))

    (it "closes and invokes onclose when client is connecting"
      (wjs/o-set @sock "readyState" 0)
      (wjs/add-listener @sock "close" (stub :close))
      (server/close @sock)
      (should (ws/closed? @sock))
      (should-have-invoked :close))

    (it "closes and invokes onclose when client is connected"
      (wjs/o-set @sock "readyState" 1)
      (wjs/add-listener @sock "close" (comp (stub :close) js->clj))
      (server/close @sock)
      (let [[event] (stub/last-invocation-of :close)
            clj-sock (js->clj @sock)]
        (should= true (get event "isTrusted"))
        (should= false (get event "bubbles"))
        (should= false (get event "cancelBubble"))
        (should= false (get event "cancelable"))
        (should= false (get event "composed"))
        (should= clj-sock (get event "currentTarget"))
        (should= false (get event "defaultPrevented"))
        (should= 0 (get event "eventPhase"))
        (should= true (get event "returnValue"))
        (should= clj-sock (get event "srcElement"))
        (should= clj-sock (get event "target"))
        (should= 123.4567 (get event "timeStamp"))
        (should= 1000 (get event "code"))
        (should= "closed by server" (get event "reason"))
        (should= true (get event "wasClean"))
        (should= "close" (get event "type"))))

    (it "close event references socket object"
      (wjs/o-set @sock "readyState" 1)
      (wjs/add-listener @sock "close" (stub :close))
      (server/close @sock)
      (let [[event] (stub/last-invocation-of :close)]
        (should= @sock (wjs/o-get event "currentTarget"))
        (should= @sock (wjs/o-get event "srcElement"))
        (should= @sock (wjs/o-get event "target"))))
    )

  (context "send"

    (it "throws when client is CONNECTING"
      (should-throw js/Error "Socket is not OPEN" (server/send @sock "the message")))

    (it "throws when client is CLOSING"
      (wjs/o-set @sock "readyState" 2)
      (should-throw js/Error "Socket is not OPEN" (server/send @sock "the message")))

    (it "throws when client is CLOSED"
      (wjs/o-set @sock "readyState" 3)
      (should-throw js/Error "Socket is not OPEN" (server/send @sock "the message")))

    (it "invokes message handler"
      (wjs/o-set @sock "readyState" 1)
      (wjs/add-listener @sock "message" (comp (stub :message) js->clj))
      (server/send @sock "the message")
      (let [[event] (stub/last-invocation-of :message)
            clj-sock (js->clj @sock)]
        (should= true (get event "isTrusted"))
        (should= false (get event "bubbles"))
        (should= false (get event "cancelBubble"))
        (should= false (get event "cancelable"))
        (should= false (get event "composed"))
        (should= clj-sock (get event "currentTarget"))
        (should= "the message" (get event "data"))
        (should= false (get event "defaultPrevented"))
        (should= 0 (get event "eventPhase"))
        (should= "" (get event "lastEventId"))
        (should= "ws://example.com" (get event "origin"))
        (should= [] (get event "ports"))
        (should= true (get event "returnValue"))
        (should= clj-sock (get event "srcElement"))
        (should= clj-sock (get event "target"))
        (should= 123.4567 (get event "timeStamp"))
        (should= "message" (get event "type"))))

    (it "message handler event references websocket object"
      (wjs/o-set @sock "readyState" 1)
      (wjs/add-listener @sock "message" (stub :message))
      (server/send @sock "the message")
      (let [[event] (stub/last-invocation-of :message)]
        (should= @sock (wjs/o-get event "currentTarget"))
        (should= @sock (wjs/o-get event "srcElement"))
        (should= @sock (wjs/o-get event "target"))))

    (it "origin excludes the websocket URL's path"
      (wjs/o-set @sock "readyState" 1)
      (wjs/o-set @sock "url" "ws://localhost:1234/foo")
      (wjs/add-listener @sock "message" (stub :message))
      (server/send @sock "the message")
      (let [[event] (stub/last-invocation-of :message)]
        (should= "ws://localhost:1234" (wjs/o-get event "origin"))))

    )

  (context "reject"

    (it "throws when socket is already open"
      (wjs/o-set @sock "readyState" 1)
      (should-throw js/Error "Socket is not CONNECTING" (server/reject @sock 4000 "nope")))

    (it "throws when socket is closing"
      (wjs/o-set @sock "readyState" 2)
      (should-throw js/Error "Socket is not CONNECTING" (server/reject @sock 4000 "nope")))

    (it "throws when socket is closed"
      (wjs/o-set @sock "readyState" 3)
      (should-throw js/Error "Socket is not CONNECTING" (server/reject @sock 4000 "nope")))

    (it "invokes onerror and onclose"
      (wjs/add-listener @sock "close" (stub :close))
      (wjs/add-listener @sock "error" (stub :error))
      (server/reject @sock 4000 "nope")
      (should (ws/closed? @sock))
      (should-have-invoked :error)
      (should-have-invoked :close))

    (it "onerror event data"
      (wjs/add-listener @sock "error" (comp (stub :error) js->clj))
      (server/reject @sock 4000 "nope")
      (let [[event] (stub/last-invocation-of :error)
            clj-sock (js->clj @sock)]
        (should= true (get event "isTrusted"))
        (should= false (get event "bubbles"))
        (should= false (get event "cancelBubble"))
        (should= false (get event "cancelable"))
        (should= false (get event "composed"))
        (should= clj-sock (get event "currentTarget"))
        (should= 0 (get event "eventPhase"))
        (should= true (get event "returnValue"))
        (should= clj-sock (get event "srcElement"))
        (should= clj-sock (get event "target"))
        (should= 123.4567 (get event "timeStamp"))
        (should= "error" (get event "type"))))

    (it "onclose event data"
      (wjs/add-listener @sock "close" (comp (stub :close) js->clj))
      (server/reject @sock 4000 "nope")
      (let [[event] (stub/last-invocation-of :close)
            clj-sock (js->clj @sock)]
        (should= true (get event "isTrusted"))
        (should= false (get event "bubbles"))
        (should= false (get event "cancelBubble"))
        (should= false (get event "cancelable"))
        (should= 4000 (get event "code"))
        (should= false (get event "composed"))
        (should= clj-sock (get event "currentTarget"))
        (should= 0 (get event "eventPhase"))
        (should= "nope" (get event "reason"))
        (should= true (get event "returnValue"))
        (should= clj-sock (get event "srcElement"))
        (should= clj-sock (get event "target"))
        (should= 123.4567 (get event "timeStamp"))
        (should= "close" (get event "type"))
        (should= false (get event "wasClean"))))

    (it "onclose event references socket object"
      (wjs/add-listener @sock "close" (stub :close))
      (server/reject @sock 4001 "blah")
      (let [[event] (stub/last-invocation-of :close)]
        (should= 4001 (wjs/o-get event "code"))
        (should= @sock (wjs/o-get event "currentTarget"))
        (should= "blah" (wjs/o-get event "reason"))
        (should= @sock (wjs/o-get event "srcElement"))
        (should= @sock (wjs/o-get event "target"))))
    )

  (context "shutdown"

    (it "throws when performing any operation after being shutdown"
      (server/shutdown)
      (should-throw js/Error "Server is not running" (server/shutdown))
      (should-throw js/Error "Server is not running" (server/open @sock))
      (should-throw js/Error "Server is not running" (server/reject @sock 4000 "blah"))
      (should-throw js/Error "Server is not running" (server/close @sock))
      (should-throw js/Error "Server is not running" (server/send @sock "foo")))

    (it "invokes onerror for all active sockets"
      (let [sock-2 (mem-socket/->MemSocket "ws://example.com")]
        (wjs/add-listener @sock "error" (stub :error-1))
        (wjs/add-listener sock-2 "error" (stub :error-2))
        (server/shutdown)
        (should (ws/closed? @sock))
        (should (ws/closed? sock-2))
        (should-have-invoked :error-1)
        (should-have-invoked :error-2)))

    (it "onerror event data"
      (wjs/add-listener @sock "error" (comp (stub :error) js->clj))
      (server/shutdown)
      (let [[event] (stub/last-invocation-of :error)
            clj-sock (js->clj @sock)]
        (should= true (get event "isTrusted"))
        (should= false (get event "bubbles"))
        (should= false (get event "cancelBubble"))
        (should= false (get event "cancelable"))
        (should= false (get event "composed"))
        (should= clj-sock (get event "currentTarget"))
        (should= false (get event "defaultPrevented"))
        (should= 0 (get event "eventPhase"))
        (should= true (get event "returnValue"))
        (should= clj-sock (get event "srcElement"))
        (should= clj-sock (get event "target"))
        (should= 123.4567 (get event "timeStamp"))
        (should= "error" (get event "type"))))

    (it "onerror event references socket object"
      (wjs/add-listener @sock "error" (stub :error))
      (server/shutdown)
      (let [[event] (stub/last-invocation-of :error)]
        (should= @sock (wjs/o-get event "currentTarget"))
        (should= @sock (wjs/o-get event "srcElement"))
        (should= @sock (wjs/o-get event "target"))))

    (it "invokes onclose for all active sockets"
      (let [sock-2 (mem-socket/->MemSocket "ws://example.com")]
        (wjs/add-listener @sock "close" (stub :close-1))
        (wjs/add-listener sock-2 "close" (stub :close-2))
        (server/shutdown)
        (should-have-invoked :close-1)
        (should-have-invoked :close-2)))

    (it "onclose event data"
      (wjs/add-listener @sock "close" (comp (stub :close) js->clj))
      (server/shutdown)
      (let [[event] (stub/last-invocation-of :close)
            clj-sock (js->clj @sock)]
        (should= true (get event "isTrusted"))
        (should= false (get event "bubbles"))
        (should= false (get event "cancelBubble"))
        (should= false (get event "cancelable"))
        (should= 1006 (get event "code"))
        (should= false (get event "composed"))
        (should= clj-sock (get event "currentTarget"))
        (should= false (get event "defaultPrevented"))
        (should= 0 (get event "eventPhase"))
        (should= "" (get event "reason"))
        (should= true (get event "returnValue"))
        (should= clj-sock (get event "srcElement"))
        (should= clj-sock (get event "target"))
        (should= 123.4567 (get event "timeStamp"))
        (should= "close" (get event "type"))
        (should= false (get event "wasClean"))))

    (it "onclose event references socket object"
      (wjs/add-listener @sock "close" (stub :close))
      (server/shutdown)
      (let [[event] (stub/last-invocation-of :close)]
        (should= @sock (wjs/o-get event "currentTarget"))
        (should= @sock (wjs/o-get event "srcElement"))
        (should= @sock (wjs/o-get event "target"))))

    (it "onerror is invoked before onclose"
      (let [atm (atom nil)]
        (wjs/add-listener @sock "error" #(reset! atm :error))
        (wjs/add-listener @sock "close" #(reset! atm :close))
        (server/shutdown)
        (should= :close @atm)))

    (it "does nothing with closing sockets"
      (wjs/add-listener @sock "error" (stub :error))
      (wjs/add-listener @sock "close" (stub :close))
      (wjs/o-set @sock "readyState" 2)
      (server/shutdown)
      (should-not-have-invoked :close)
      (should-not-have-invoked :error)
      (should (ws/closing? @sock)))

    (it "does nothing with closed sockets"
      (wjs/add-listener @sock "error" (stub :error))
      (wjs/add-listener @sock "close" (stub :close))
      (wjs/o-set @sock "readyState" 3)
      (server/shutdown)
      (should-not-have-invoked :close)
      (should-not-have-invoked :error)
      (should (ws/closed? @sock)))
    )
  )
