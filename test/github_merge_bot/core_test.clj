(ns github-merge-bot.core-test
  (:require [clojure.test :refer :all]
            [github-merge-bot.core :refer :all]
            [tentacles.pulls :as pulls]))

(deftest test-head-up-to-date-with-base
  (testing
    (let [pr (pulls/specific-pull "sdduursma"  "github-merge-bot-test" 18)]
      (is (not (head-up-to-date-with-base? "sdduursma" "github-merge-bot-test" pr))))
    (let [pr (pulls/specific-pull "sdduursma" "github-merge-bot-test" 19)]
      (is (head-up-to-date-with-base? "sdduursma" "github-merge-bot-test" pr)))))
