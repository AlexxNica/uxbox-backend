(ns uxbox.tests.test-auth
  (:require [clojure.test :as t]
            [promesa.core :as p]
            [clj-http.client :as http]
            [catacumba.testing :refer (with-server)]
            [buddy.hashers :as hashers]
            [uxbox.db :as db]
            [uxbox.frontend.routes :as urt]
            [uxbox.services.users :as usu]
            [uxbox.services :as usv]
            [uxbox.tests.helpers :as th]))

(t/use-fixtures :each th/database-reset)

(t/deftest test-http-success-auth
  (let [data {:username "user1"
              :fullname "user 1"
              :metadata "1"
              :password  "user1"
              :email "user1@uxbox.io"}
        user (with-open [conn (db/connection)]
               (usu/create-user conn data))]
    (with-server {:handler (urt/app)}
      (let [data {:username "user1"
                  :password "user1"
                  :metadata "1"
                  :scope "foobar"}
            uri (str th/+base-url+ "/api/auth/token")
            [status data] (th/http-post uri {:body data})]
        ;; (println "RESPONSE:" status data)
        (t/is (= status 200))
        (t/is (contains? data :token))))))

(t/deftest test-http-failed-auth
  (let [data {:username "user1"
              :fullname "user 1"
              :metadata "1"
              :password  (hashers/encrypt "user1")
              :email "user1@uxbox.io"}
        user (with-open [conn (db/connection)]
               (usu/create-user conn data))]
    (with-server {:handler (urt/app)}
      (let [data {:username "user1"
                  :password "user2"
                  :metadata "2"
                  :scope "foobar"}
            uri (str th/+base-url+ "/api/auth/token")
            [status data] (th/http-post uri {:body data})]
        ;; (println "RESPONSE:" status data)
        (t/is (= 400 status))
        (t/is (= (:type data) :auth/wrong-credentials))))))

