(ns bwa.mimic.memory-websocket-spec
  (:require-macros [speclj.core :refer [before context describe it redefs-around should-contain should-have-invoked should-not-have-invoked should-not-throw should-throw should= stub with with-stubs]])
  (:require [bwa.mimic.memory-websocket :as sut]
            [bwa.mimic.server :as server]
            [bwa.mimic.spec-helperc :as spec-helperc]
            [bwa.mimic.websocket :as ws]
            [c3kit.apron.log :as log]
            [c3kit.wire.js :as wjs]
            [clojure.string :as str]
            [speclj.stub :as stub]))

(declare sock)

(defn should-have-invoked-close-event
  ([name sock code reason] (should-have-invoked-close-event name sock code reason true))
  ([name sock code reason clean?]
   (let [[event] (stub/last-invocation-of name)]
     (should= sock (wjs/o-get event "currentTarget"))
     (should= sock (wjs/o-get event "srcElement"))
     (should= sock (wjs/o-get event "target"))
     (should= reason (wjs/o-get event "reason"))
     (should= "close" (wjs/o-get event "type"))
     (should= code (wjs/o-get event "code"))
     (should= 0 (wjs/o-get event "eventPhase"))
     (should= 123.4567 (wjs/o-get event "timeStamp"))
     (should= true (wjs/o-get event "isTrusted"))
     (should= true (wjs/o-get event "returnValue"))
     (should= clean? (wjs/o-get event "wasClean"))
     (should= false (wjs/o-get event "bubbles"))
     (should= false (wjs/o-get event "cancelBubble"))
     (should= false (wjs/o-get event "cancelable"))
     (should= false (wjs/o-get event "composed"))
     (should= false (wjs/o-get event "defaultPrevented")))))

(describe "Memory WebSocket"
  (with-stubs)
  (spec-helperc/capture-logs-around)
  (before (wjs/o-set js/performance "now" (fn [] 123.4567)))

  (with sock (sut/->MemSocket "ws://example.com/foo"))
  (redefs-around [server/initiate (stub :server/initiate)])


  (context "constructor"

    (it "new socket"
      (should-have-invoked :server/initiate {:with [@sock]})
      (should= "ws://example.com/foo" (wjs/o-get @sock "url"))
      (should= "blob" (wjs/o-get @sock "binaryType"))
      (should= 0 (wjs/o-get @sock "bufferedAmount"))
      (should= 0 (wjs/o-get @sock "readyState"))
      (should= "" (wjs/o-get @sock "extensions"))
      (should= "" (wjs/o-get @sock "protocol")))

    (it "specifies a protocol"
      (let [sock (sut/->MemSocket "ws://example.com/foo" "theProtocol")]
        (should= "theProtocol" (wjs/o-get sock "protocol"))))

    (it "specifies a protocol in a coll"
      (let [sock (sut/->MemSocket "ws://example.com/foo" (clj->js ["theProtocol"]))]
        (should= "theProtocol" (wjs/o-get sock "protocol"))))

    (it "specifies two protocols"
      (let [sock (sut/->MemSocket "ws://example.com/foo" (clj->js ["foo" "bar"]))]
        (should= "foo" (wjs/o-get sock "protocol"))))

    (it "throws when protocol is neither a string nor a collection"
      (should-throw (sut/->MemSocket "ws://example.com/foo" 1)))

    (it "throws when a protocol is specified more than once"
      (should-throw js/SyntaxError "Protocols may not contain duplicates: [\"foo\" \"bar\" \"foo\"]"
                    (sut/->MemSocket "ws://example.com/foo" (clj->js ["foo" "bar" "foo"]))))

    (it "throws when protocol is not ws:// or wss://"
      (should-throw js/SyntaxError "URL is invalid: http://example.com/foo"
                    (sut/->MemSocket "http://example.com/foo"))
      (should-throw js/SyntaxError (sut/->MemSocket "https://example.com/foo"))
      (should-throw js/SyntaxError (sut/->MemSocket "ws:/example.com/foo"))
      (should-throw js/SyntaxError (sut/->MemSocket "wss:/example.com/foo"))
      (should-throw js/SyntaxError (sut/->MemSocket "example.com/foo"))
      (should-not-throw (sut/->MemSocket "ws://example.com/foo"))
      (should-not-throw (sut/->MemSocket "wss://example.com/foo")))

    (it "throws when a fragment exists in the url"
      (should-throw js/SyntaxError "URL is invalid: ws://example.com/foo#"
                    (sut/->MemSocket "ws://example.com/foo#"))
      (should-throw js/SyntaxError (sut/->MemSocket "ws://example.com/foo#blah"))
      (should-throw js/SyntaxError (sut/->MemSocket "ws://example.c#om/middle")))
    )

  (context "send"

    (it "throws when in CONNECTING state"
      (should-throw js/Error "MemSocket is still in CONNECTING state." (ws/send! @sock "data")))

    (it "does nothing when in CONNECTED state"
      (wjs/o-set @sock "readyState" 1)
      (ws/send! @sock "data")
      (should= "" (log/captured-logs-str)))

    (it "logs error when in CLOSING state"
      (wjs/o-set @sock "readyState" 2)
      (ws/send! @sock "data")
      (should= "MemSocket is already in CLOSING or CLOSED state." (log/captured-logs-str)))

    (it "logs error when in CLOSED state"
      (wjs/o-set @sock "readyState" 3)
      (ws/send! @sock "data")
      (should= "MemSocket is already in CLOSING or CLOSED state." (log/captured-logs-str)))

    )

  (context "on open"

    (it "no handler set"
      (should-not-throw (js-invoke @sock "onopen" (js-obj "foo" "bar"))))

    (it "invokes sole event handler"
      (wjs/add-listener @sock "open" (stub :on-open))
      (let [event (js-obj "foo" "bar")]
        (js-invoke @sock "onopen" event)
        (should-have-invoked :on-open {:with [event]})))

    (it "invokes two event handlers"
      (wjs/add-listener @sock "open" (stub :on-open-1))
      (wjs/add-listener @sock "open" (stub :on-open-2))
      (let [event (js-obj "foo" "bar")]
        (js-invoke @sock "onopen" event)
        (should-have-invoked :on-open-1 {:with [event]})
        (should-have-invoked :on-open-2 {:with [event]})))

    (it "exception in the middle of the callback loop"
      (wjs/add-listener @sock "open" (stub :on-open-1))
      (wjs/add-listener @sock "open" (stub :on-open-2 {:throw "open error"}))
      (wjs/add-listener @sock "open" (stub :on-open-3))
      (let [event (js-obj "data" "blah")]
        (js-invoke @sock "onopen" event)
        (should-have-invoked :on-open-1 {:with [event]})
        (should-have-invoked :on-open-2 {:with [event]})
        (should-have-invoked :on-open-3 {:with [event]})
        (should-contain "Error occurred in MemSocket onopen open error" (log/captured-logs-str))))
    )

  (context "on message"
    (it "no handler set"
      (should-not-throw (js-invoke @sock "onmessage" (js-obj "data" "blah"))))

    (it "invokes sole event handler"
      (wjs/add-listener @sock "message" (stub :on-message))
      (let [event (js-obj "data" "blah")]
        (js-invoke @sock "onmessage" event)
        (should-have-invoked :on-message {:with [event]})))

    (it "invokes two event handlers"
      (wjs/add-listener @sock "message" (stub :on-message-1))
      (wjs/add-listener @sock "message" (stub :on-message-2))
      (let [event (js-obj "data" "blah")]
        (js-invoke @sock "onmessage" event)
        (should-have-invoked :on-message-1 {:with [event]})
        (should-have-invoked :on-message-2 {:with [event]})))

    (it "exception in the middle of the callback loop"
      (wjs/add-listener @sock "message" (stub :on-message-1))
      (wjs/add-listener @sock "message" (stub :on-message-2 {:throw "message error"}))
      (wjs/add-listener @sock "message" (stub :on-message-3))
      (let [event (js-obj "data" "blah")]
        (js-invoke @sock "onmessage" event)
        (should-have-invoked :on-message-1 {:with [event]})
        (should-have-invoked :on-message-2 {:with [event]})
        (should-have-invoked :on-message-3 {:with [event]})
        (should-contain "Error occurred in MemSocket onmessage message error" (log/captured-logs-str))))
    )

  (context "on error"
    (it "no handler set"
      (js-invoke @sock "onerror" (js-obj "foo" "bar")))

    (it "invokes sole event handler"
      (wjs/add-listener @sock "error" (stub :on-error))
      (let [event (js-obj "foo" "bar")]
        (js-invoke @sock "onerror" event)
        (should-have-invoked :on-error {:with [event]})))

    (it "invokes two event handlers"
      (wjs/add-listener @sock "error" (stub :on-error-1))
      (wjs/add-listener @sock "error" (stub :on-error-2))
      (let [event (js-obj "foo" "bar")]
        (js-invoke @sock "onerror" event)
        (should-have-invoked :on-error-1 {:with [event]})
        (should-have-invoked :on-error-2 {:with [event]})))

    (it "exception in the middle of the callback loop"
      (wjs/add-listener @sock "error" (stub :on-error-1))
      (wjs/add-listener @sock "error" (stub :on-error-2 {:throw "error handler error"}))
      (wjs/add-listener @sock "error" (stub :on-error-3))
      (let [event (js-obj "foo" "bar")]
        (js-invoke @sock "onerror" event)
        (should-have-invoked :on-error-1 {:with [event]})
        (should-have-invoked :on-error-2 {:with [event]})
        (should-have-invoked :on-error-3 {:with [event]})
        (should-contain "Error occurred in MemSocket onerror error handler error" (log/captured-logs-str))))
    )

  (context "close"
    (before (wjs/o-set @sock "readyState" 1))

    (it "updates readyState to 3"
      (ws/close! @sock)
      (should= 3 (wjs/o-get @sock "readyState")))

    (it "defaults closing code to 1000"
      (ws/close! @sock)
      (should= "Closing MemSocket with code: 1000" (log/captured-logs-str)))

    (it "code is neither 1000 nor an integer between 3000 and 4999"
      (should-throw (js-invoke @sock "close" 2999))
      (should-throw (js-invoke @sock "close" 5000))
      (should-throw (js-invoke @sock "close" 2999 "reason"))
      (should-throw (js-invoke @sock "close" 5000 "reason"))
      (should-not-throw (js-invoke @sock "close" 1000))
      (should-not-throw (js-invoke @sock "close" 3000))
      (should-not-throw (js-invoke @sock "close" 4000))
      (should-not-throw (js-invoke @sock "close" 4999))
      (should-not-throw (js-invoke @sock "close" 1000 "reason"))
      (should-not-throw (js-invoke @sock "close" 3000 "reason"))
      (should-not-throw (js-invoke @sock "close" 4000 "reason"))
      (should-not-throw (js-invoke @sock "close" 4999 "reason")))

    (it "closes with specified code"
      (js-invoke @sock "close" 3000)
      (should= 3 (wjs/o-get @sock "readyState"))
      (should= "Closing MemSocket with code: 3000" (log/captured-logs-str)))

    (it "closes with reason"
      (js-invoke @sock "close" 3000 "just because")
      (should= 3 (wjs/o-get @sock "readyState"))
      (let [[log-1 log-2] (str/split (log/captured-logs-str) "\n")]
        (should= "Closing MemSocket with code: 3000" log-1)
        (should= "Closing MemSocket with reason: just because" log-2)))

    (it "closes with reason of exactly 123 bytes"
      (let [reason (apply str (repeat 123 "a"))]
        (js-invoke @sock "close" 1000 reason)
        (should= 3 (wjs/o-get @sock "readyState"))
        (let [[log-1 log-2] (str/split (log/captured-logs-str) "\n")]
          (should= "Closing MemSocket with code: 1000" log-1)
          (should= (str "Closing MemSocket with reason: " reason) log-2))))

    (it "throws if reason code is greater than 123 bytes and does not close"
      (should-throw (js-invoke @sock "close" 1000 (apply str (repeat 124 "a"))))
      (should= 1 (wjs/o-get @sock "readyState"))
      (should= "" (log/captured-logs-str)))

    (it "counts reason size via UTF-8 encoding"
      (let [emoji-char "🥸" ; 4 bytes
            reason     (apply str (repeat 31 emoji-char))]
        (should-throw (js-invoke @sock "close" 1000 reason))
        (should= 1 (wjs/o-get @sock "readyState"))
        (should= "" (log/captured-logs-str))))

    (context "on close handler"
      (it "unassigned"
        (should-not-throw (js-invoke @sock "onclose")))

      (it "already closed"
        (ws/close! @sock)
        (ws/close! @sock))

      (it "on-close fires when close is invoked"
        (wjs/add-listener @sock "close" (stub :on-close))
        (ws/close! @sock)
        (should-have-invoked-close-event :on-close @sock 1000 ""))

      (it "on-close fires when close is invoked with a code and reason"
        (wjs/add-listener @sock "close" (stub :on-close))
        (js-invoke @sock "close" 3000 "the reason")
        (should-have-invoked-close-event :on-close @sock 3000 "the reason"))

      (it "onclose does not fire when reason is too long"
        (let [reason (apply str (repeat 124 "a"))]
          (wjs/add-listener @sock "close" (stub :on-close))
          (should-throw (js-invoke @sock "close" 1000 reason))
          (should-not-have-invoked :on-close)))

      (it "onclose fires many event handlers"
        (wjs/add-listener @sock "close" (stub :handler-1))
        (wjs/add-listener @sock "close" (stub :handler-2))
        (js-invoke @sock "close" 3000 "another reason")
        (should-have-invoked-close-event :handler-1 @sock 3000 "another reason")
        (should-have-invoked-close-event :handler-2 @sock 3000 "another reason"))

      (it "close handler throws, but does not stop remaining handlers"
        (wjs/add-listener @sock "close" (stub :handler-1))
        (wjs/add-listener @sock "close" (stub :handler-2 {:throw "the error"}))
        (wjs/add-listener @sock "close" (stub :handler-3))
        (js-invoke @sock "close" 3000 "another reason")
        (should-have-invoked-close-event :handler-1 @sock 3000 "another reason" false)
        (should-have-invoked-close-event :handler-2 @sock 3000 "another reason" false)
        (should-have-invoked-close-event :handler-3 @sock 3000 "another reason" false)
        (should-contain "Error occurred in MemSocket onclose the error" (log/captured-logs-str)))
      )
    )
  )
