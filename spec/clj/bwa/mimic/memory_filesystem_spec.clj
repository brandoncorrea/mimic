(ns bwa.mimic.memory-filesystem-spec
  (:require [bwa.mimic.memory-filesystem :as sut]
            [speclj.core :refer :all]))

(declare file)

(describe "Memory Filesystem"

  (context "acting like a file"
    (before (sut/clear!))

    (it "createNewFile"
      (let [file (sut/->File "blah.txt")]
        (should= false (.exists file))
        (should= false (.isFile file))
        (should= true (.createNewFile file))
        (should= true (.isFile file))
        (should= true (.exists file))
        (should= false (.createNewFile file))
        (should= true (.isFile file))
        (should= true (.exists file))))

    (it "deletes a file"
      (let [file (sut/->File "blah.txt")]
        (should= true (.createNewFile file))
        (should= true (.delete file))
        (should= false (.isFile file))
        (should= false (.exists file))
        (should= false (.delete file))))

    (it "isDirectory"
      (let [dir (sut/->File "blah.txt")]
        (should= false (.isDirectory dir))
        (should= true (.mkdir dir))
        (should= true (.isDirectory dir))
        (should= true (.exists dir))
        (should= false (.mkdir dir))
        (should= true (.isDirectory dir))
        (should= true (.exists dir))))

    (it "deletes a directory"
      (let [dir (sut/->File "blah.txt")]
        (should= true (.mkdir dir))
        (should= true (.delete dir))
        (should= false (.isDirectory dir))
        (should= false (.exists dir))
        (should= false (.delete dir))))

    (it "file information is shared across objects"
      (let [f1 (sut/->File "blah.txt")
            f2 (sut/->File "blah.txt")]
        (.createNewFile f1)
        (should= true (.exists f2))))
    )

  )
