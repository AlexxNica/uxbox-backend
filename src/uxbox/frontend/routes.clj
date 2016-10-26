;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.frontend.routes
  (:require [catacumba.core :as ct]
            [catacumba.http :as http]
            [catacumba.serializers :as sz]
            [catacumba.handlers.auth :as cauth]
            [catacumba.handlers.parse :as cparse]
            [catacumba.handlers.misc :as cmisc]
            [uxbox.config :as cfg]
            [uxbox.services.auth :as sauth]
            [uxbox.frontend.auth :as auth]
            [uxbox.frontend.users :as users]
            [uxbox.frontend.errors :as errors]
            [uxbox.frontend.projects :as projects]
            [uxbox.frontend.pages :as pages]
            [uxbox.frontend.images :as images]
            [uxbox.frontend.icons :as icons]
            [uxbox.frontend.debug-emails :as dbgemails]
            [uxbox.util.response :refer (rsp)]
            [uxbox.util.uuid :as uuid]))

(defn- welcome-api
  "A GET entry point for the api that shows
  a welcome message."
  [context]
  (let [body {:message "Welcome to UXBox api."}]
    (-> (sz/encode body :json)
        (http/ok {:content-type "application/json"}))))

(defn- authorization
  [{:keys [identity] :as context}]
  (if identity
    (ct/delegate {:identity (uuid/from-string (:id identity))})
    (http/forbidden (rsp {:message "Forbidden"}))))

(defn- debug-only
  [context]
  (if (-> cfg/config :server :debug)
    (ct/delegate)
    (http/not-found "")))

(def cors-conf
  {:origin "*"
   :max-age 3600
   :allow-methods #{:post :put :get :delete :trace}
   :allow-headers #{:x-requested-with :content-type :authorization}})

(defn app
  []
  (let [props {:secret sauth/secret
               :options sauth/+auth-opts+}
        backend (cauth/jwe-backend props)]
    (ct/routes
     [[:any (cauth/auth backend)]
      [:any (cmisc/autoreloader)]

      [:get "api" #'welcome-api]
      [:assets "media" {:dir "public/media"}]
      [:assets "static" {:dir "public/static"}]

      [:prefix "debug"
       [:any debug-only]
       [:get "emails" #'dbgemails/list-emails]
       [:get "emails/email" #'dbgemails/show-email]]

      [:prefix "api"
       [:any (cmisc/cors cors-conf)]
       [:any (cparse/body-params)]
       [:error #'errors/handler]

       [:post "auth/token" #'auth/login]
       [:post "auth/register" #'users/register-user]
       [:get  "auth/recovery/:token" #'users/validate-recovery-token]
       [:post "auth/recovery" #'users/request-recovery]
       [:put  "auth/recovery" #'users/recover-password]

       [:get "projects-by-token/:token" #'projects/retrieve-project-by-share-token]

       [:any #'authorization]

       ;; Projects
       [:get "projects/:id/pages" #'pages/list-pages-by-project]
       [:put "projects/:id" #'projects/update-project]
       [:delete "projects/:id" #'projects/delete-project]
       [:post "projects" #'projects/create-project]
       [:get "projects" #'projects/list-projects]

       ;; Image Collections
       [:put "library/image-collections/:id" #'images/update-collection]
       [:delete "library/image-collections/:id" #'images/delete-collection]
       [:get "library/image-collections" #'images/list-collections]
       [:post "library/image-collections" #'images/create-collection]
       [:get "library/image-collections/:id/images" #'images/list-images]
       [:get "library/image-collections/images" #'images/list-images]

       ;; Images
       [:delete "library/images/:id" #'images/delete-image]
       [:get "library/images/:id" #'images/retrieve-image]
       [:put "library/images/:id" #'images/update-image]
       [:post "library/images" #'images/create-image]

       ;; Icon Collections
       [:put "library/icon-collections/:id" #'icons/update-collection]
       [:delete "library/icon-collections/:id" #'icons/delete-collection]
       [:get "library/icon-collections" #'icons/list-collections]
       [:post "library/icon-collections" #'icons/create-collection]
       [:get "library/icon-collections/:id/icons" #'icons/list-icons]
       [:get "library/icon-collections/icons" #'icons/list-icons]

       ;; Icons
       [:delete "library/icons/:id" #'icons/delete-icon]
       [:put "library/icons/:id" #'icons/update-icon]
       [:post "library/icons" #'icons/create-icon]

       ;; Pages
       [:put "pages/:id/metadata" #'pages/update-page-metadata]
       [:get "pages/:id/history" #'pages/retrieve-page-history]
       [:put "pages/:id/history/:hid" #'pages/update-page-history]
       [:put "pages/:id" #'pages/update-page]
       [:delete "pages/:id" #'pages/delete-page]
       [:post "pages" #'pages/create-page]

       ;; Profile
       [:get "profile/me" #'users/retrieve-profile]
       [:put "profile/me" #'users/update-profile]
       [:put "profile/me/password" #'users/update-password]
       [:post "profile/me/photo" #'users/update-photo]]])))

