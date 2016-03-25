(ns uxbox.tests.test-pages
  (:require [clojure.test :as t]
            [promesa.core :as p]
            [suricatta.core :as sc]
            [clj-uuid :as uuid]
            [catacumba.testing :refer (with-server)]
            [buddy.core.codecs :as codecs]
            [uxbox.persistence :as up]
            [uxbox.frontend.routes :as urt]
            [uxbox.services.projects :as uspr]
            [uxbox.services.pages :as uspg]
            [uxbox.services :as usv]
            [uxbox.tests.helpers :as th]))

(t/use-fixtures :each th/database-reset)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontend Test
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(t/deftest test-http-page-create
  (with-open [conn (up/get-conn)]
    (let [user (th/create-user conn 1)
          proj (uspr/create-project conn {:user (:id user) :name "proj1"})]
      (with-server {:handler (urt/app)}
        (let [uri (str th/+base-url+ "/api/pages")
              params {:body {:project (:id proj)
                             :name "page1"
                             :data [:test]
                             :width 200
                             :height 200
                             :layout "mobile"}}
              [status data] (th/http-post user uri params)]
          ;; (println "RESPONSE:" status data)
          (t/is (= 201 status))
          (t/is (= (:data (:body params)) (:data data)))
          (t/is (= (:user data) (:id user)))
          (t/is (= (:name data) "page1")))))))

(t/deftest test-http-page-update
  (with-open [conn (up/get-conn)]
    (let [user (th/create-user conn 1)
          proj (uspr/create-project conn {:user (:id user) :name "proj1"})
          data {:id (uuid/v4)
                :user (:id user)
                :project (:id proj)
                :version 0
                :data (th/data-encode [:test1])
                :name "page1"
                :width 200
                :height 200
                :layout "mobil"}
          page (uspg/create-page conn data)]
      (with-server {:handler (urt/app)}
        (let [uri (str th/+base-url+ (str "/api/pages/" (:id page)))
              params {:body (assoc page :data [:test1 :test2])}
              [status page'] (th/http-put user uri params)]
          ;; (println "RESPONSE:" status page')
          (t/is (= 200 status))
          (t/is (= [:test1 :test2] (:data page')))
          (t/is (= 1 (:version page')))
          (t/is (= (:user page') (:id user)))
          (t/is (= (:name data) "page1")))))))

(t/deftest test-http-page-update-metadata
  (with-open [conn (up/get-conn)]
    (let [user (th/create-user conn 1)
          proj (uspr/create-project conn {:user (:id user) :name "proj1"})
          data {:id (uuid/v4)
                :user (:id user)
                :project (:id proj)
                :version 0
                :data (th/data-encode [:test1])
                :name "page1"
                :width 200
                :height 200
                :layout "mobil"}
          page (uspg/create-page conn data)]
      (with-server {:handler (urt/app)}
        (let [uri (str th/+base-url+ (str "/api/pages/" (:id page) "/metadata"))
              params {:body (assoc page :data [:test1 :test2])}
              [status page'] (th/http-put user uri params)]
          ;; (println "RESPONSE:" status page')
          (t/is (= 200 status))
          (t/is (= [:test1] (:data page')))
          (t/is (= 1 (:version page')))
          (t/is (= (:user page') (:id user)))
          (t/is (= (:name data) "page1")))))))

(t/deftest test-http-page-delete
  (with-open [conn (up/get-conn)]
    (let [user (th/create-user conn 1)
          proj (uspr/create-project conn {:user (:id user) :name "proj1"})
          data {:id (uuid/v4)
                :user (:id user)
                :project (:id proj)
                :version 0
                :data (th/data-encode [:test1])
                :name "page1"
                :width 200
                :height 200
                :layout "mobil"}
          page (uspg/create-page conn data)]
      (with-server {:handler (urt/app)}
        (let [uri (str th/+base-url+ (str "/api/pages/" (:id page)))
              [status response] (th/http-delete user uri)]
          ;; (println "RESPONSE:" status response)
          (t/is (= 204 status))
          (let [sqlv ["SELECT * FROM pages WHERE \"user\"=?" (:id user)]
                result (sc/fetch conn sqlv)]
            (t/is (empty? result))))))))

(t/deftest test-http-page-list
  (with-open [conn (up/get-conn)]
    (let [user (th/create-user conn 1)
          proj (uspr/create-project conn {:user (:id user) :name "proj1"})
          data {:id (uuid/v4)
                :user (:id user)
                :project (:id proj)
                :version 0
                :data (th/data-encode [:test1])
                :name "page1"
                :width 200
                :height 200
                :layout "mobil"}
          page (uspg/create-page conn data)]
      (with-server {:handler (urt/app)}
        (let [uri (str th/+base-url+ (str "/api/pages"))
              [status response] (th/http-get user uri)]
          ;; (println "RESPONSE:" status response)
          (t/is (= 200 status))
          (t/is (= 1 (count response))))))))

(t/deftest test-http-page-list-by-project
  (with-open [conn (up/get-conn)]
    (let [user (th/create-user conn 1)
          proj1 (uspr/create-project conn {:user (:id user) :name "proj1"})
          proj2 (uspr/create-project conn {:user (:id user) :name "proj2"})
          data {:user (:id user)
                :version 0
                :data (th/data-encode [])
                :name "page1"
                :width 200
                :height 200
                :layout "mobil"}
          page1 (uspg/create-page conn (assoc data :project (:id proj1)))
          page2 (uspg/create-page conn (assoc data :project (:id proj2)))]
      (with-server {:handler (urt/app)}
        (let [uri (str th/+base-url+ (str "/api/projects/" (:id proj1) "/pages"))
              [status response] (th/http-get user uri)]
          ;; (println "RESPONSE:" status response)
          (t/is (= 200 status))
          (t/is (= 1 (count response)))
          (t/is (= (:id (first response)) (:id page1))))))))

(t/deftest test-http-page-history-retrieve
  (with-open [conn (up/get-conn)]
    (let [user (th/create-user conn 1)
          proj (uspr/create-project conn {:user (:id user) :name "proj1"})
          data {:id (uuid/v4)
                :user (:id user)
                :project (:id proj)
                :version 0
                :data (th/data-encode [:test1])
                :name "page1"
                :width 200
                :height 200
                :layout "mobil"}
          page (uspg/create-page conn data)]

      (dotimes [i 100]
        (let [page (uspg/get-page-by-id conn (:id data))]
          (uspg/update-page conn (assoc page :data (th/data-encode [i])))))

      ;; Check inserted history
      (let [sqlv ["SELECT * FROM pages_history WHERE page=?" (:id data)]
            result (sc/fetch conn sqlv)]
        (t/is (= (count result) 100)))

      (with-server {:handler (urt/app)}
        (let [uri (str th/+base-url+ "/api/pages/" (:id page) "/history")
              [status result] (th/http-get user uri nil)]
          ;; (println "RESPONSE:" status result)
          (t/is (= (count result) 10))
          (t/is (= 200 status))
          (t/is (= 99 (:version (first result))))

          (let [params {:query {:since (:version (last result))
                                :max 20}}
                [status result] (th/http-get user uri params)]
            ;; (println "RESPONSE:" status result)
            (t/is (= (count result) 20))
            (t/is (= 200 status))
            (t/is (= 89 (:version (first result))))))))))

