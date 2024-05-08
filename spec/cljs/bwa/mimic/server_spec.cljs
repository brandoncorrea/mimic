(ns bwa.mimic.server-spec
  (:require-macros [speclj.core :refer [before context describe it should-not-throw should=]])
  (:require [bwa.mimic.server :as sut]
            [c3kit.wire.js :as wjs]
            [speclj.core]))

(def sock (js-obj "sock" "et"))

(describe "Server Socket"

  (it "repl options"
    (should= sut/initiate (wjs/o-get sut/repl-options "initiate"))
    (should= sut/open (wjs/o-get sut/repl-options "open"))
    (should= sut/reject (wjs/o-get sut/repl-options "reject"))
    (should= sut/close (wjs/o-get sut/repl-options "close"))
    (should= sut/send (wjs/o-get sut/repl-options "send"))
    (should= sut/shutdown (wjs/o-get sut/repl-options "shutdown")))

  (context "default"
    (before (reset! sut/impl nil))

    (it "initiate"
      (should-not-throw (sut/initiate sock)))

    (it "open"
      (should-not-throw (sut/open sock)))

    (it "reject"
      (should-not-throw (sut/reject sock 0 "blah")))

    (it "close"
      (should-not-throw (sut/close sock)))

    (it "send"
      (should-not-throw (sut/send sock "blah")))

    (it "shutdown"
      (should-not-throw (sut/shutdown)))

    )
  )
