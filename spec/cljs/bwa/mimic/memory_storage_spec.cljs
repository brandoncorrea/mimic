(ns bwa.mimic.memory-storage-spec
  (:require [bwa.mimic.memory-storage :as sut]
            [c3kit.wire.js :as wjs]
            [speclj.core :refer-macros [context describe should-not-throw should-throw should-be-a it should= should-be-nil should-fail with]]))

(declare store)
(defn repeat-str [s n] (js-invoke s "repeat" n))
(defn o-keys [obj] (js->clj (sut/o-keys obj)))
(defn o-values [obj] (js->clj (js-invoke js/Object "values" obj)))
(defn o-entries [obj] (js->clj (sut/o-entries obj)))

(describe "Memory Storage"

  (with store (sut/->MemStorage))

  (it "initialized"
    (should-be-a js/Object @store)
    (should= [] (o-keys @store))
    (should= [] (o-values @store))
    (should= [] (o-entries @store)))

  (it "adds a key-value pair"
    (sut/set-item @store "foo" "bar")
    (should= ["foo"] (o-keys @store))
    (should= ["bar"] (o-values @store))
    (should= [["foo" "bar"]] (o-entries @store))
    (should= "bar" (sut/get-item @store "foo")))

  (it "adds two key-value pairs"
    (sut/set-item @store "foo" "bar")
    (sut/set-item @store "baz" "buzz")
    (should= "bar" (sut/get-item @store "foo"))
    (should= "buzz" (sut/get-item @store "baz")))

  (it "removes a key"
    (sut/set-item @store "foo" "bar")
    (sut/set-item @store "baz" "buzz")
    (sut/remove-item @store "foo")
    (should-be-nil (sut/get-item @store "foo"))
    (should= "buzz" (sut/get-item @store "baz")))

  (it "removes two keys"
    (sut/set-item @store "foo" "bar")
    (sut/set-item @store "baz" "buzz")
    (sut/remove-item @store "foo")
    (sut/remove-item @store "baz")
    (should= [] (o-entries @store))
    (should-be-nil (sut/get-item @store "foo"))
    (should-be-nil (sut/get-item @store "baz")))

  (it "clears all key-value pairs"
    (sut/set-item @store "foo" "bar")
    (sut/set-item @store "baz" "buzz")
    (sut/clear @store)
    (should= [] (o-entries @store))
    (should-be-nil (sut/get-item @store "foo"))
    (should-be-nil (sut/get-item @store "bar")))

  (it "sets a nil value"
    (sut/set-item @store "foo" nil)
    (should= "null" (sut/get-item @store "foo")))

  (it "sets an undefined value"
    (sut/set-item @store "foo" js/undefined)
    (should= "undefined" (sut/get-item @store "foo")))

  (it "sets a nil key"
    (sut/set-item @store nil "foo")
    (should= "foo" (sut/get-item @store nil))
    (should= "foo" (sut/get-item @store "null"))
    (should-be-nil (sut/get-item @store js/undefined))
    (should-be-nil (sut/get-item @store "undefined")))

  (it "sets an undefined key"
    (sut/set-item @store js/undefined "foo")
    (should= "foo" (sut/get-item @store js/undefined))
    (should= "foo" (sut/get-item @store "undefined"))
    (should-be-nil (sut/get-item @store nil))
    (should-be-nil (sut/get-item @store "null")))

  (it "removes nil key by nil value"
    (sut/set-item @store nil "foo")
    (sut/remove-item @store nil)
    (should-be-nil (sut/get-item @store nil))
    (should-be-nil (sut/get-item @store "null")))

  (it "removes nil key by 'null' string"
    (sut/set-item @store nil "foo")
    (sut/remove-item @store "null")
    (should-be-nil (sut/get-item @store nil))
    (should-be-nil (sut/get-item @store "null")))

  (it "removes undefined key by undefined value"
    (sut/set-item @store js/undefined "foo")
    (sut/remove-item @store js/undefined)
    (should-be-nil (sut/get-item @store js/undefined))
    (should-be-nil (sut/get-item @store "undefined")))

  (it "removes undefined key by 'undefined' string"
    (sut/set-item @store js/undefined "foo")
    (sut/remove-item @store "undefined")
    (should-be-nil (sut/get-item @store js/undefined))
    (should-be-nil (sut/get-item @store "undefined")))

  (it "sets key to a JavaScript object"
    (let [k1 (js-obj {"foo" "bar"})
          k2 (js-obj {"baz" "buzz"})]
      (sut/set-item @store k1 "blah")
      (should= "blah" (sut/get-item @store k1))
      (should= "blah" (sut/get-item @store k2))
      (should= "blah" (sut/get-item @store "[object Object]"))))

  (it "sets key to a named JavaScript object"
    (let [k1 (js/Array. 2)
          k2 (js/Array. 4)]
      (sut/set-item @store k1 "two")
      (sut/set-item @store k2 "four")
      (should= "two" (sut/get-item @store k1))
      (should= "four" (sut/get-item @store k2))
      (should= "two" (sut/get-item @store ","))
      (should= "four" (sut/get-item @store ",,,"))))

  (it "sets key to a clojure map"
    (sut/set-item @store {:foo :bar} "baz")
    (should= "baz" (sut/get-item @store {:foo :bar}))
    (should= "baz" (sut/get-item @store "{:foo :bar}")))

  (it "sets value to a clojure map"
    (sut/set-item @store "baz" {:foo :bar})
    (should= "{:foo :bar}" (sut/get-item @store "baz")))

  (it "sets value to a JavaScript object"
    (sut/set-item @store "baz" (js-obj "foo" "bar"))
    (should= "[object Object]" (sut/get-item @store "baz")))

  (it "exceeds quota size"
    (should-not-throw
      (sut/set-item @store (repeat-str "x" (/ sut/default-quota 2)) ""))
    (try
      (sut/set-item @store "x" "")
      (should-fail "expected DOMException to be thrown")
      (catch js/Error e
        (should-be-a js/DOMException e)
        (should= "QuotaExceededError" (wjs/o-get e "name"))
        (should= "MemStorage quota has been exceeded. Quota: 5242880, Size: 5242882" (wjs/o-get e "message")))))

  (it "configurable quota"
    (let [store (sut/->MemStorage 2)]
      (should-not-throw (sut/set-item store "x" ""))
      (try
        (sut/set-item store "y" "")
        (should-fail "expected DOMException to be thrown")
        (catch js/Error e
          (should-be-a js/DOMException e)
          (should= "QuotaExceededError" (wjs/o-get e "name"))
          (should= "MemStorage quota has been exceeded. Quota: 2, Size: 4" (wjs/o-get e "message"))))
      (should= "" (sut/get-item store "x"))))
  )
