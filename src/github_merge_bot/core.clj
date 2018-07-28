(ns github-merge-bot.core
  (:require [tentacles.pulls :as pulls]
            [clojure.pprint :as pprint]
            [clojure.tools.logging :as log]
            [clj-jgit.porcelain :refer :all])
  (:import (java.util UUID))
  (:gen-class))

(defn mergeable? [pull-id]
  (:mergeable (pulls/specific-pull "sdduursma" "github-merge-bot-test" pull-id)))

; TODO: `last` correct?
(defn oldest [pulls]
  (last (sort-by :created-at pulls)))

(defn pull-requests-to-update
  "Pull requests to update with their base branch."
  [pulls]
  [(oldest (filter #(mergeable? (:number %))
                  pulls))])

(defn update-pull [owner repo pull-request]
  ;; TODO: Clone every time?
  (let [repo (:repo (git-clone-full (str "https://github.com/" owner "/" repo ".git")
                                        (str "./tmp/" (UUID/randomUUID) owner "/" repo)))
        head-branch (:ref (:head pull-request))]
    (println "Cloned repo to" (.getPath (.getDirectory (.getRepository repo))))
    (git-fetch repo "origin")
    (git-checkout repo head-branch true false (str "origin/" head-branch))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [pull-requests (pull-requests-to-update (pulls/pulls "sdduursma" "github-merge-bot-test"))]
    (doseq [pr pull-requests]
      (update-pull "sdduursma" "github-merge-bot-test" pr))))
