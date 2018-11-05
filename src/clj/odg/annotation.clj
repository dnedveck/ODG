(ns odg.annotation
  (:import (org.neo4j.graphdb Transaction)
           (org.neo4j.unsafe.batchinsert BatchInserterIndex))
  (:require clojure.java.io
            clojure.string
            [clojure.core.reducers :as r]
            [odg.util :as util]
            [odg.db :as db]
            [loom.graph :as graph]
            [loom.alg :as graph-alg]
            [clojure.core.async
                        :as async
                        :refer
                                [chan >! >!! <!
                                 <!! close! go-loop
                                 dropping-buffer thread]]
            [biotools.gff :as gff]
            [biotools.gtf :as gtf]
            [odg.batch :as batch]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn generic-entry
  [id type start end strand phase landmark species version note additional-data]
  (merge
    additional-data ; Should always be overwritten by passed arguments
    {:id id
     :type type
     :start start
     :end end
     :strand strand
     :phase phase
     :landmark landmark
     :note note
     :species species
     :version version}))

(defn get-exon-node
  "Checks for the existence of the exon node and creates it, if necessary.
  If it is created it is connected to its parent transcript/mRNA node as well"
  [create-fn idx-query entry transcript]
  (let [exon-id (str (:oid entry) "_exon_" (:exon_number entry))
        db-entry (idx-query exon-id)]
    (if-not @db-entry
      (let [exon (create-fn
                   ; Create the gene entry
                   (generic-entry
                     exon-id
                     "exon"
                     (:start entry)
                     (:end entry)
                     (:strand entry)
                     (:phase entry)
                     (:landmark entry)
                     (:species entry)
                     (:version entry)
                     "Autogenerated from GTF import"
                     entry))]
        ;(batch/create-rel transcript exon (:PARENT_OF db/rels))
        exon)
      db-entry)))

; OUTDATED
(defn import-gtf
  "Batch import GTF file into existing database. Differs very little over the
   GFF parser. Has additional support for creating gene records, however.
   Designed for cufflinks created GTF files at this time."
  [species version filename]

  (error "import-gtf called but is outdated")

  (let [species-label (batch/dynamic-label species)
        version-label (batch/dynamic-label (str species " " version))
        labels (partial into [species-label version-label])
        species_ver_root 1]


    (println "Importing GTF annotation for" species version "from" filename)

    (with-open [rdr (clojure.java.io/reader filename)]
      (let [all-entries
            (group-by
              (memoize (fn [x] (util/remove-transcript-id (:oid x))))
              (vec (gtf/parse-reader-reducer rdr)))]
        (merge
          {:indices [(batch/convert-name species version)]}
          (apply
            merge-with
            concat
            (for [[gene-id entries] all-entries]
              (when (and gene-id (seq entries))
                {:nodes-create-if-new
                 (distinct
                   (concat
                     ; Create "gene" node (if necessary)
                     (list
                       (let [entry (first entries)]
                         [{:id gene-id
                           :start (apply min (remove nil? (map :start entries)))
                           :end (apply max (remove nil? (map :end entries)))
                           :note "Autogenerated from GTF file"
                           :strand (:strand entry)
                           :phase (:phase entry)
                           :landmark (:landmark entry)
                           :species species
                           :version version
                           :cufflinks_gene_id (:gene_id entry)}

                          (labels [(:GENE batch/labels) (:ANNOTATION batch/labels)])

                          ; optional rels to create if node is created
                          (for [landmark (util/get-landmarks
                                           (:landmark entry)
                                           (apply min (remove nil? (map :start entries)))
                                           (apply max (remove nil? (map :end entries))))]
                            [(:LOCATED_ON db/rels) gene-id landmark])]))


                     ; Create transcript nodes if necessary
                     (distinct
                       (for [[transcript-id transcript-entries] (group-by :oid entries)]
                         [{:id transcript-id
                           :start (apply min (map :start entries))
                           :end (apply max (map :end entries))
                           :species species
                           :version version
                           :note "Autogenerated from GTF import"
                           :cufflinks_gene_id (:gene_id (first entries))
                           :transcript_id (:transcript_id (first entries))}
                          (labels [(:MRNA batch/labels) (:ANNOTATION batch/labels)])
                          [(:PARENT_OF db/rels) gene-id transcript-id]]))))}))))))))

                 ; TODO: Also check or add exon nodes from GTF files! Not priority


(defn import-gtf-cli
  "Import annotation - helper fn for when run from the command-line (opposed to entire database generation)"
  [config opts args]

;  (batch/connect (get-in config [:global :db_path] (:memory opts)))
  (import-gtf (:species opts) (:version opts) (first args)))

; This fn does not run in batch mode.
; CYPHER does not work on batch databases; but this is still fast enough of
; an operation to run independently.
(defn create-gene-neighbors
  "Works on all species in the database at once. Does not function in batch
      mode. Incompatabile with batch operation database."
  ;;; [config opts args]
  [config opts _]
  (info "Creating NEXT_TO relationships for all genomes")
  (db/connect (get-in config [:global :db_path]) (:memory opts))
  ; Get ordered list by chr, ignore strandedness, sort by gene.start
  (db/query (str "MATCH (x:Landmark)
                    <-[:LOCATED_ON]-
                      (:LandmarkHash)
                    <-[:LOCATED_ON]-(gene)
                  WHERE (gene:gene OR gene:Annotation OR gene:Gene
                         or gene:annotation)
                  RETURN x.species, x.version, x.id,
                    gene.`odg-filename` AS filename, gene
                  ORDER BY x.species, x.version, x.id, filename, gene.start") {}
   (info "Results obtained, now creating relationships...")
   (doseq [[[species version filename id] genes]
           (group-by
            (fn [x]
              [(get x "x.species")
               (get x "x.version")
               (get x "x.id")
               (get x "filename")]
              results))]
     (doseq [[a b] (partition 2 1 genes)]
       ; We are in a transaction, so don't use db/create-relationship here!
       (.createRelationshipTo
        (get a "gene")
        (get b "gene")
        (:NEXT_TO db/rels))))))

(defn create-parent-of-rels
  [nodes]
  (let [entries (map first nodes)
        odg-id-map (into
                    {}
                    (map
                     (juxt :id :odg-id)
                     entries))
        nodes-with-parents (filter :parent entries)]
    (for [entry nodes-with-parents
          parent-id (clojure.string/split (:parent entry) #"\s*,\s*")]
      [(:PARENT_OF db/rels)
       (get odg-id-map parent-id)
       (get odg-id-map (:id entry))])))

(defn create-located-on-rels
  [nodes]
  (for [node nodes
        landmark (util/get-landmarks
                  (:landmark node)
                  (:start node)
                  (:end node))]
    [(:LOCATED_ON db/rels) (:odg-id node) landmark]))

(defn create-node
  [gff-entry]
  (-> [gff-entry []] ; Start node entry with no labels
    util/wrap-urldecode
    util/wrap-add-label-from-type
    util/wrap-add-missing-id
    (util/wrap-add-label "Annotation")))

(defn convert-to-graph [nodes]
  (let [entries (map first nodes)
        anno-map (into {} (map (juxt :id identity) entries))
        graph-data (for [entry entries]
                     (if (:parent entry)
                       [(:parent entry) (:id entry)]
                       (:id entry)))]
    (def graph-data graph-data)
    (apply
     graph/digraph
     graph-data)))

; Files created with Augustus - CGP pipeline will be proper GFF3's
; with the exception of missing "gene" entries.

(defn missing-mrna-parents? [nodes]
  (let [entries (map first nodes)
        node-map (into {} (map (juxt :id identity)) entries)
        mrna-nodes (filter (fn [x] (= (:type x) "mRNA")) entries)]
    (not
      (every?
       (fn [x] (get node-map x))
       (filter
        identity
        (map :parent mrna-nodes))))))

; Adds gene entries to GFF3 files. Potentially could be
; made to repair entire gene annotation graphs....
(defn add-missing-mrna-parents [nodes]
  (let [entries (map first nodes)
        node-map (into {} (map (juxt :id identity)) entries)
        ; mRNA nodes with parent entry but lacking parents
        mrna-nodes (filter
                    (fn [x]
                      (and
                       (= (:type x) "mRNA")
                       (nil? (get node-map (:parent x)))))
                    entries)
        mrna-nodes-id (map :id mrna-nodes)
        mrna-nodes-id-hash (into #{} mrna-nodes-id)

        node-graph (convert-to-graph nodes)
        connected-subgraphs (graph-alg/connected-components node-graph)]

    (for [i connected-subgraphs]
      (let [g (into
               {}
               (map
                (fn [x]
                  [x (get node-map x)])
                i))

            my-nodes (filter identity (map second g))

            nil-entries (filter
                         (fn [x]
                           (nil?
                            (second x)))
                         g)]

        (for [[id _] nil-entries]
          (if (= 0 (graph/in-degree node-graph id))
            [(generic-entry
               id
               "gene"
               (apply min (map :start my-nodes))
               (apply max (map :end my-nodes))
               (some :strand my-nodes)
               "."
               (some :landmark my-nodes)
               (some :species my-nodes)
               (some :version my-nodes)
               "Entry autogenerated by ODG"
               {})
             [(batch/dynamic-label "Annotation")
              (batch/dynamic-label "gene")
              (batch/dynamic-label "Autogenerated")]]))))))


(defn import-gff
  "Batch import GFF file into existing database."
  [species version filename]

  (info "Importing annotation for" species version filename)

  ; Keep the reader outside to facilitate automatic closing of the file
  (let [species-label  (batch/dynamic-label species)
        version-label  (batch/dynamic-label (str species " " version))
        filename-label (batch/dynamic-label (batch/convert-name filename))
        labels (partial into [species-label version-label])
        species_ver_root 1] ; TODO: Fix me!

    (with-open [rdr (clojure.java.io/reader filename)]
      (let [nodes-raw (map create-node (gff/parse-reader rdr))
            nodes-fixed (if (missing-mrna-parents? nodes-raw)
                          (do
                            (error filename " is missing mRNA parents (genes, most likely). Automatic repair in progress.")
                            (apply concat nodes-raw (add-missing-mrna-parents nodes-raw)))
                          nodes-raw)
            nodes (doall
                    (map
                     (fn [x]
                      (-> x
                        (util/wrap-add-labels [species-label version-label filename-label])
                        (util/wrap-create-odg-id-from-properties species version filename)
                        (util/wrap-add-property :odg-filename filename)))
                     nodes-fixed))
            node-graph (convert-to-graph nodes)
            node-map (into {} (map (juxt :id identity) (map first nodes)))
            loners (graph-alg/loners node-graph)
            top-level-node-ids (filter
                                 (fn [x]
                                   (= 0 (graph/in-degree node-graph x)))
                                 (graph/nodes node-graph))
            top-level-nodes (for [id top-level-node-ids]
                              (get node-map id))
            job {:species species
                 :version version
                 :nodes nodes
                 :rels (concat
                        (create-parent-of-rels nodes)
                        (create-located-on-rels top-level-nodes))
                 :indices [(batch/convert-name species version)]}]

        (info "Identified " (count loners) " in " filename)
        job))))

(defn import-gff-cli
  "Import annotation - helper fn for when run from the command-line (opposed to entire database generation)"
  [config opts args]

  ;(batch/connect (get-in config [:global :db_path]) (:memory opts))
  (import-gff (:species opts) (:version opts) (first args)))
