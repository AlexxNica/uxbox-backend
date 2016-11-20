;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.cli.collimp
  "Collection importer command line helper."
  (:require [clojure.spec :as s]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [mount.core :as mount]
            [cuerdas.core :as str]
            [suricatta.core :as sc]
            [storages.core :as st]
            [storages.util :as fs]
            [uxbox.config]
            [uxbox.db :as db]
            [uxbox.migrations]
            [uxbox.media :as media]
            [uxbox.cli.sql :as sql]
            [uxbox.util.spec :as us]
            [uxbox.util.cli :as cli]
            [uxbox.util.uuid :as uuid]
            [uxbox.util.data :as data])
  (:import [java.io Reader PushbackReader]
           [javax.imageio ImageIO]))

;; --- Constants & Specs

(def ^:const +imates-uuid-ns+ #uuid "3642a582-565f-4070-beba-af797ab27a6e")

(s/def ::name string?)
(s/def ::type keyword?)
(s/def ::path string?)
(s/def ::regex us/regex?)

(s/def ::import-entry
  (s/keys :req-un [::name ::type ::path ::regex]))

;; --- CLI Helpers


(defn printerr
  [& args]
  (binding [*out* *err*]
    (apply println args)))

(defn pushback-reader
  [reader]
  (PushbackReader. ^Reader reader))

;; --- Colors Collections Importer

(def storage media/images-storage)

(defn- create-image-collection
  "Create or replace image collection by its name."
  [conn {:keys [name] :as entry}]
  (let [id (uuid/namespaced +imates-uuid-ns+ name)
        sqlv (sql/create-image-collection {:id id :name name})]
    (sc/execute conn sqlv)
    id))

(defn- retrieve-image-size
  [path]
  (let [path (fs/path path)
        file (.toFile path)
        buff (ImageIO/read file)]
    [(.getWidth buff)
     (.getHeight buff)]))

(defn- retrieve-image
  [conn id]
  {:pre [(uuid? id)]}
  (let [sqlv (sql/get-image {:id id})]
    (some->> (sc/fetch-one conn sqlv)
             (data/normalize-attrs))))

(defn- delete-image
  [conn {:keys [id path] :as image}]
  {:pre [(uuid? id)
         (fs/path? path)]}
  (let [sqlv (sql/delete-image {:id id})]
    @(st/delete storage path)
    (sc/execute conn sqlv)))

(defn- create-image
  [conn collid imageid localpath]
  {:pre [(fs/path? localpath)
         (uuid? collid)
         (uuid? imageid)]}
  (let [filename (fs/base-name localpath)
        [width height] (retrieve-image-size localpath)
        extension (second (fs/split-ext filename))
        path @(st/save storage filename localpath)
        params {:name filename
                :path (str path)
                :mimetype (case extension
                            ".jpg" "image/jpeg"
                            ".png" "image/png")
                :width width
                :height height
                :collection collid
                :id imageid}
        sqlv (sql/create-image params)]
    (sc/execute conn sqlv)))

(defn- import-image
  [conn id fpath]
  {:pre [(uuid? id) (fs/path? fpath)]}
  (let [imageid (uuid/namespaced +imates-uuid-ns+ (str id fpath))]
    (if-let [image (retrieve-image conn imageid)]
      (do
        (delete-image conn image)
        (create-image conn id imageid fpath))
      (create-image conn id imageid fpath))))

(defn- process-images-entry
  [conn {:keys [path regex] :as entry}]
  {:pre [(s/valid? ::import-entry entry)]}
  (let [id (create-image-collection conn entry)]
    (doseq [fpath (fs/list-files path)]
      (when (re-matches regex (str fpath))
        (import-image conn id fpath)))))

;; --- Entry Point

(defn- check-path!
  [path]
  (when-not path
    (cli/print-err! "No path is provided.")
    (cli/exit! -1))
  (when-not (fs/exists? path)
    (cli/print-err! "Path does not exists.")
    (cli/exit! -1))
  (when (fs/directory? path)
    (cli/print-err! "The provided path is a directory.")
    (cli/exit! -1))
  (fs/path path))

(defn- read-import-file
  [path]
  (let [path (check-path! path)
        parent (fs/parent path)
        reader (pushback-reader (io/reader path))]
    [parent (read reader)]))

(defn- start-system
  []
  (-> (mount/only #{#'uxbox.config/config
                    #'uxbox.db/datasource
                    #'uxbox.migrations/migrations})
      (mount/start)))

(defn- stop-system
  []
  (mount/stop))

(defn- run-importer
  [directory data]
  (println "Running importer on:")
  (pprint data))

(defn main
  [& [path]]
  (let [[directory data] (read-import-file path)]
    (start-system)
    (try
      (run-importer directory data)
      (finally
        (stop-system)))))
