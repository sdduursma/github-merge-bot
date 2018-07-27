(ns github-merge-bot.core
  (:require [tentacles.pulls :as pulls]
            [clojure.pprint :as pprint]
            [clojure.tools.logging :as log])
  (:gen-class))

(defn mergeable? [pull-id]
  (:mergeable (pulls/specific-pull "sdduursma" "github-merge-bot-test" pull-id)))

; TODO: `last` correct?
(defn oldest [pulls]
  (last (sort-by :created-at pulls)))

(defn pull-to-update
  "Pull requests to update with its base branch."
  [pulls]
  (oldest (filter #(mergeable? (:number %))
                  pulls)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!")
  (pprint/pprint (:number (pull-to-update (pulls/pulls "sdduursma" "github-merge-bot-test")))))
