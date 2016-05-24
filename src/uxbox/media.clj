;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.media
  "A media storage impl for uxbox."
  (:require [mount.core :as mount :refer (defstate)]
            [clojure.java.io :as io]
            [cuerdas.core :as str]
            [storages.core :as st]
            [storages.fs :refer (filesystem)]
            [storages.misc :refer (hashed scoped)]
            [uxbox.config :refer (config)]))

(defstate static-storage
  :start (let [{:keys [basedir baseuri]} (:static config)]
           (filesystem {:basedir basedir :baseuri baseuri})))

(defstate media-storage
  :start (let [{:keys [basedir baseuri]} (:media config)]
           (filesystem {:basedir basedir :baseuri baseuri})))

(defstate images-storage
  :start (-> media-storage
             (scoped "images")
             (hashed)))

(defstate thumbnails-storage
  :start (scoped media-storage "thumbs"))
