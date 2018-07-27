(ns github-merge-bot.core-test
  (:require [clojure.test :refer :all]
            [github-merge-bot.core :refer :all]))

(deftest test-pull-to-update
  (testing
    (is (= [] (pull-to-update [])))
    (is (= [{:number 1}] (pull-to-update [{:number 1}])))))
