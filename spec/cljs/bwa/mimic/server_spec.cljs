(ns bwa.mimic.server-spec
  (:require-macros [speclj.core :refer [before context describe it should-be should-be-a should-be-nil should=]])
  (:require [bwa.mimic.server :as sut]
            [c3kit.wire.js :as wjs]
            [speclj.core]))

(def sock (js-obj "sock" "et"))

(describe "Server Socket"
  (before (reset! sut/impl nil))

  (context "repl options"

    (it "repl options"
      (should= sut/initiate (wjs/o-get sut/repl-options "initiate"))
      (should= sut/open (wjs/o-get sut/repl-options "open"))
      (should= sut/reject (wjs/o-get sut/repl-options "reject"))
      (should= sut/close (wjs/o-get sut/repl-options "close"))
      (should= sut/send (wjs/o-get sut/repl-options "send"))
      (should= sut/receive (wjs/o-get sut/repl-options "receive"))
      (should= sut/flush (wjs/o-get sut/repl-options "flush"))
      (should= sut/shutdown (wjs/o-get sut/repl-options "shutdown")))

    (it "connections"
      (let [connections (js-invoke sut/repl-options "connections")]
        (should-be-a js/Array connections)
        (should-be empty? connections)))

    (it "messages"
      (let [messages (js-invoke sut/repl-options "messages" :ws)]
        (should-be-a js/Array messages)
        (should-be empty? messages)))
    )

  (context "default"
    (before (reset! sut/impl nil))

    (it "connections"
      (should-be-nil (sut/connections)))

    (it "open"
      (should-be-nil (sut/open sock)))

    (it "reject"
      (should-be-nil (sut/reject sock 0 "blah")))

    (it "close"
      (should-be-nil (sut/close sock)))

    (it "send"
      (should-be-nil (sut/send sock "blah")))

    (it "receive"
      (should-be-nil (sut/receive sock "blah")))

    (it "messages"
      (should-be-nil (sut/messages sock)))

    (it "flush"
      (should-be-nil (sut/flush sock)))

    (it "shutdown"
      (should-be-nil (sut/shutdown)))

    (it "initiate"
      (should-be-nil (sut/initiate sock)))

    )
  )
