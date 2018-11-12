(ns odg.assembly
  "Functions for handling the FASTA files of assemblies"
  (:require clojure.java.io
            [odg.util :as util]
            [odg.batch :as batch]
            [biotools.fasta :as fasta]
            [taoensso.timbre :as timbre]
            [clojure.core.reducers :as r]
            [biotools.gff :as gff]
            [odg.job :as job]
            [odg.db :as db])
  (:import (org.neo4j.unsafe.batchinsert BatchInserterIndex)))

(set! *warn-on-reflection* true)

(timbre/refer-timbre)

(defn compute-nodes-fn
  [species version labels]
  (fn compute-nodes
    [[landmark length]]
    (concat
      [(job/->Node
          {:id (clojure.string/lower-case landmark)
           :length length
           :species species
           :version version}
          (labels [(:LANDMARK batch/labels)]))]
      (for [mark (range 0 (* 100000 (+ 2 (int (/ length 100000)))) 100000)]
        (job/->Node {:id (clojure.string/lower-case (str landmark ":" mark))}
          (labels [(:LANDMARK_HASH batch/labels)]))))))

(defn compute-rels-fn
  [species version]
  (fn compute-rels
    [[landmark length]]
    (let [marks (range 0 (* 100000 (+ 2 (int (/ length 100000)))) 100000)
          lc-landmark (clojure.string/lower-case landmark)] ; Only convert to lowercase once
       (concat
         [(job/rel (:BELONGS_TO db/rels)
                   lc-landmark
                   (job/species-version-odg-id species version))]
         (for [mark marks]
          (job/rel
           (:LOCATED_ON db/rels)
           (clojure.string/lower-case (str landmark ":" mark))
           landmark))
         (for [[f s] (partition 2 1 marks)]
           (job/rel
            (:NEXT_TO db/rels)
            (str lc-landmark ":" f)
            (str lc-landmark ":" s)))))))

(defn get-landmarks
  [filename]
  (with-open [rdr (clojure.java.io/reader filename)]
    (into {}
          (map
            (fn [r] {(:id r) (count (:seq r))})
            (if (re-find #"gff" filename)
              (gff/parse-fasta rdr)
              (fasta/parse rdr))))))

; Generates a message to the db-handler actor/server now, instead of sending off jobs itself
; Should send a single package of things to do to the db-handler...

(defn landmark-odg-id [landmark species version]
  (str "landmark-id-" landmark "-" species "-" version))

(defn landmarkhash-odg-id [landmark species version mark]
  (str "landmarkhash-id-" landmark ":" mark "-" species "-" version))

; Test command
(defn import-fasta
  "FASTA sequences are used here to create a pseudomolecule backbone to attach genes and other elements to; the assembly gives coordinates to elements"

   [species version filename]

   (info "Importing assembly for:" species version filename)

   (let [species-label (batch/dynamic-label species)
         version-label (batch/dynamic-label (str species " " version))
         labels (partial into [species-label version-label])

         compute-nodes (compute-nodes-fn species version labels)
         compute-rels (compute-rels-fn species version)

         landmarks (get-landmarks filename)
         nodes (mapcat compute-nodes landmarks)
         rels (mapcat compute-rels landmarks)]

     {:species species
      :version version
      :nodes nodes
      :rels rels
      :indices [(batch/convert-name species version)]}))

;(defn create-landmark-nodes [landmarks species version]
;  (for [[landmark length] landmarks]
;    [(odg.job/->Node
;      {:id (clojure.string/lower-case landmark)
;       :length length
;       :species species
;       :version version
;      (labels [(:LANDMARK batch/labels)])}))
