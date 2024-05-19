(ns bwa.mimic.memory-filesystem
  (:require [clojure.java.io :as io])
  (:import (clojure.java.io IOFactory)
           (java.io File FileInputStream FileOutputStream InputStream OutputStream Reader Writer)
           (java.net MalformedURLException URL)))

(defonce file-system (atom {}))
(defn clear! [] (reset! file-system {}))

(defn- identifier [^File f] (.getAbsolutePath f))

(defn- exists? [^File f]
  (contains? @file-system (identifier f)))

(defn- details [^File f]
  (get @file-system (identifier f)))

(defn- kind [^File f] (:kind (details f)))
(defn- create [^File f & {:as opts}]
  (and (not (exists? f))
       (swap! file-system assoc (identifier f) opts)))

(defn- delete [^File f]
  (and (exists? f)
       (swap! file-system dissoc (identifier f))))

(defn- create-new-file [^File f]
  (create f :kind :file))

(defn- mkdir [^File f]
  (create f :kind :directory))

(defn ->File [path]
  (proxy [File] [path]
    (createNewFile [] (boolean (create-new-file this)))
    (exists [] (exists? this))
    (delete [] (boolean (delete this)))
    (isFile [] (= :file (kind this)))
    (mkdir [] (boolean (mkdir this)))
    (isDirectory [] (= :directory (kind this)))))

(comment "Spec Helper will probably look something like this"
  (defn up []
    (extend-protocol io/Coercions
      String
      (as-file [s] (->File s))))

  (defn down []
    (extend-protocol io/Coercions
      String
      (as-file [s] (File. s)))
    (extend String
      IOFactory
      (assoc io/default-streams-impl
        :make-input-stream
        (fn [^String x opts]
          (try
            (io/make-input-stream (URL. x) opts)
            (catch MalformedURLException e
              (io/make-input-stream (File. x) opts))))
        :make-output-stream
        (fn [^String x opts]
          (try
            (io/make-output-stream (URL. x) opts)
            (catch MalformedURLException err
              (io/make-output-stream (File. x) opts))))))
    (extend File
      IOFactory
      (assoc io/default-streams-impl
        :make-input-stream
        (fn [^File x opts]
          (io/make-input-stream (FileInputStream. x) opts))
        :make-output-stream
        (fn [^File x opts]
          (io/make-output-stream (FileOutputStream. x (boolean (:append opts))) opts))))
    (extend URL
      IOFactory
      (assoc io/default-streams-impl
        :make-input-stream
        (fn [^URL x opts]
          (io/make-input-stream
            (if (= "file" (.getProtocol x))
              (FileInputStream. ^File (io/as-file x))
              (.openStream x)) opts))
        :make-output-stream
        (fn [^URL x opts]
          (if (= "file" (.getProtocol x))
            (io/make-output-stream (io/as-file x) opts)
            (throw (IllegalArgumentException. (str "Can not write to non-file URL <" x ">")))))))
    (defmethod io/do-copy [InputStream File] [^InputStream input ^File output opts]
      (with-open [out (FileOutputStream. output)]
        (io/do-copy input out opts)))
    (defmethod io/do-copy [Reader File] [^Reader input ^File output opts]
      (with-open [out (FileOutputStream. output)]
        (io/do-copy input out opts)))
    (defmethod io/do-copy [File OutputStream] [^File input ^OutputStream output opts]
      (with-open [in (FileInputStream. input)]
        (io/do-copy in output opts)))
    (defmethod io/do-copy [File Writer] [^File input ^Writer output opts]
      (with-open [in (FileInputStream. input)]
        (io/do-copy in output opts)))
    (defmethod io/do-copy [File File] [^File input ^File output _opts]
      (with-open [in  (-> input FileInputStream. .getChannel)
                  out (-> output FileOutputStream. .getChannel)]
        (let [sz (.size in)]
          (loop [pos 0]
            (let [bytes-xferred (.transferTo in pos (- sz pos) out)
                  pos           (+ pos bytes-xferred)]
              (when (< pos sz)
                (recur pos)))))))
    )

  (defn with-memory-filesystem []
    (list
      (before (memory-fs/up))
      (after (memory-fs/down))))
  )
