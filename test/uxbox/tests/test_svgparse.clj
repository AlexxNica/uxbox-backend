(ns uxbox.tests.test-svgparse
  (:require [clojure.test :as t]
            [clojure.java.io :as io]
            [catacumba.testing :refer (with-server)]
            [uxbox.frontend :as uft]
            [uxbox.services :as usv]
            [uxbox.services.svgparse :as svg]
            [uxbox.tests.helpers :as th]))

(t/use-fixtures :each th/state-init)

(t/deftest parse-svg-test
  (t/testing "parsing valid svg 1"
    (let [image (slurp (io/resource "uxbox/tests/_files/sample1.svg"))
          result (svg/parse-string image)]
      (t/is (contains? result :width))
      (t/is (contains? result :height))
      (t/is (contains? result :view-box))
      (t/is (contains? result :name))
      (t/is (= 500.0 (:width result)))
      (t/is (= 500.0 (:height result)))
      (t/is (= [0.0 0.0 500.00001 500.00001] (:view-box result)))
      (t/is (= "lock.svg" (:name result)))))

  (t/testing "parsing valid svg 2"
    (let [image (slurp (io/resource "uxbox/tests/_files/sample2.svg"))
          result (svg/parse-string image)]
      (t/is (contains? result :width))
      (t/is (contains? result :height))
      (t/is (contains? result :view-box))
      (t/is (contains? result :name))
      (t/is (= 500.0 (:width result)))
      (t/is (= 500.0 (:height result)))
      (t/is (= [0.0 0.0 500.0 500.00001] (:view-box result)))
      (t/is (= "play.svg" (:name result)))))

  (t/testing "parsing invalid data 1"
    (let [image (slurp (io/resource "uxbox/tests/_files/sample.jpg"))
          [e result] (th/try-on (svg/parse-string image))]
      (t/is (th/exception? e))
      (t/is (th/ex-info? e))
      (t/is (th/ex-with-code? e :uxbox.services.svgparse/invalid-input))))

  (t/testing "parsing invalid data 2"
    (let [[e result] (th/try-on (svg/parse-string ""))]
      (t/is (th/exception? e))
      (t/is (th/ex-info? e))
      (t/is (th/ex-with-code? e :uxbox.services.svgparse/invalid-input))))

  (t/testing "parsing invalid data 3"
    (let [[e result] (th/try-on (svg/parse-string "<svg></svg>"))]
      (t/is (th/exception? e))
      (t/is (th/ex-info? e))
      (t/is (th/ex-with-code? e :uxbox.services.svgparse/invalid-result))))
  )
