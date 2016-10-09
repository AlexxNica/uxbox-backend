;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.services.pages
  (:require [clojure.spec :as s]
            [suricatta.core :as sc]
            [buddy.core.codecs :as codecs]
            [uxbox.config :as ucfg]
            [uxbox.sql :as sql]
            [uxbox.db :as db]
            [uxbox.schema :as us]
            [uxbox.services.core :as core]
            [uxbox.services.auth :as usauth]
            [uxbox.util.time :as dt]
            [uxbox.util.data :as data]
            [uxbox.util.transit :as t]
            [uxbox.util.blob :as blob]
            [uxbox.util.uuid :as uuid]))

(declare decode-page-data)
(declare decode-page-options)
(declare encode-data)

(s/def ::data string?)
(s/def ::user uuid?)
(s/def ::project uuid?)
(s/def ::options string?)
(s/def ::width integer?)
(s/def ::height integer?)
(s/def ::layout string?)
(s/def ::max integer?)
(s/def ::pinned boolean?)
(s/def ::since integer?)

;; --- Create Page

(defn create-page
  [conn {:keys [id user project name width
                height layout data options] :as params}]
  (let [opts {:id (or id (uuid/random))
              :user user
              :project project
              :name name
              :width width
              :height height
              :layout layout
              :data (blob/encode data)
              :options (blob/encode options)}
        sqlv (sql/create-page opts)]
    (->> (sc/fetch-one conn sqlv)
         (data/normalize-attrs)
         (decode-page-data)
         (decode-page-options))))

(s/def ::create-page
  (s/keys :req-un [::data ::user ::project ::us/name
                   ::width ::height ::layout ::options]
          :opt-un [::us/id]))

(defmethod core/novelty :create-page
  [params]
  (s/assert ::create-page params)
  (with-open [conn (db/connection)]
    (create-page conn params)))

;; --- Update Page

(defn update-page
  [conn {:keys [id user project name width height
                layout data version options] :as params}]
  (let [opts {:id (or id (uuid/random))
              :user user
              :project project
              :name name
              :width width
              :version version
              :height height
              :layout layout
              :data (blob/encode data)
              :options (blob/encode options)}
        sqlv (sql/update-page opts)]
    (some-> (sc/fetch-one conn sqlv)
            (data/normalize-attrs)
            (decode-page-data)
            (decode-page-options))))

(s/def ::update-page
  (s/merge ::create-page (s/keys :req-un [::us/version])))

(defmethod core/novelty :update-page
  [params]
  (s/assert ::update-page params)
  (with-open [conn (db/connection)]
    (update-page conn params)))

;; --- Update Page Metadata

(defn update-page-metadata
  [conn {:keys [id user project name width
                height layout version options] :as params}]
  (let [opts {:id (or id (uuid/random))
              :user user
              :project project
              :name name
              :width width
              :version version
              :height height
              :layout layout
              :options (blob/encode options)}
        sqlv (sql/update-page-metadata opts)]
    (some-> (sc/fetch-one conn sqlv)
            (data/normalize-attrs)
            (decode-page-data)
            (decode-page-options))))

(s/def ::update-page-metadata
  (s/keys :req-un [::data ::user ::project ::us/name ::us/version
                   ::width ::height ::layout ::options]
          :opt-un [::us/id]))

(defmethod core/novelty :update-page-metadata
  [params]
  (s/assert ::update-page-metadata params)
  (with-open [conn (db/connection)]
    (update-page-metadata conn params)))

;; --- Delete Page

(defn delete-page
  [conn {:keys [id user] :as params}]
  (let [sqlv (sql/delete-page {:id id :user user})]
    (pos? (sc/execute conn sqlv))))

(s/def ::delete-page
  (s/keys :req-un [::user ::us/id]))

(defmethod core/novelty :delete-page
  [params]
  (s/assert ::delete-page params)
  (with-open [conn (db/connection)]
    (delete-page conn params)))

;; --- List Pages by Project

(defn get-pages-for-project
  [conn project]
  (let [sqlv (sql/get-pages-for-project {:project project})]
    (->> (sc/fetch conn sqlv)
         (map data/normalize-attrs)
         (map decode-page-data)
         (map decode-page-options))))

(defn get-pages-for-user-and-project
  [conn {:keys [user project]}]
  (let [sqlv (sql/get-pages-for-user-and-project
              {:user user :project project})]
    (->> (sc/fetch conn sqlv)
         (map data/normalize-attrs)
         (map decode-page-data)
         (map decode-page-options))))

(s/def ::list-pages-by-project
  (s/keys :req-un [::user ::project]))

(defmethod core/query :list-pages-by-project
  [params]
  (s/assert ::list-pages-by-project params)
  (with-open [conn (db/connection)]
    (get-pages-for-user-and-project conn params)))

;; --- Page History (Query)

(defn get-page-history
  [conn {:keys [id user since max pinned]
         :or {since Long/MAX_VALUE max 10}}]
  (let [sqlv (sql/get-page-history {:user user
                                    :page id
                                    :since since
                                    :max max
                                    :pinned pinned})]
    (->> (sc/fetch conn sqlv)
         (map data/normalize-attrs)
         (map decode-page-data))))

(s/def ::list-page-history
  (s/keys :req-un [::us/id ::user]
          :opt-un [::max ::pinned ::since]))

(defmethod core/query :list-page-history
  [params]
  (s/assert ::list-page-history params)
  (with-open [conn (db/connection)]
    (get-page-history conn params)))

;; --- Update Page History

(defn update-page-history
  [conn {:keys [user id label pinned]}]
  (let [sqlv (sql/update-page-history {:user user
                                       :id id
                                       :label label
                                       :pinned pinned})]
    (some-> (sc/fetch-one conn sqlv)
            (data/normalize-attrs)
            (decode-page-data))))

(s/def ::label string?)
(s/def ::update-page-history
  (s/keys :req-un [::user ::us/id ::pinned ::label]))

(defmethod core/novelty :update-page-history
  [params]
  (s/assert ::update-page-history params)
  (with-open [conn (db/connection)]
    (update-page-history conn params)))

;; --- Helpers

(defn- decode-page-options
  [{:keys [options] :as result}]
  (s/assert ::us/bytes options)
  (merge result (when options
                  {:options (blob/decode->str options)})))

(defn- decode-page-data
  [{:keys [data] :as result}]
  (s/assert ::us/bytes data)
  (merge result (when data
                  {:data (blob/decode->str data)})))

(defn get-page-by-id
  [conn id]
  (s/assert ::us/id id)
  (let [sqlv (sql/get-page-by-id {:id id})]
    (some-> (sc/fetch-one conn sqlv)
            (data/normalize-attrs)
            (decode-page-data)
            (decode-page-options))))
