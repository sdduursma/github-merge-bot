(ns github-merge-bot.core
  (:require [tentacles.pulls :as pulls]
            [clj-jgit.porcelain :as git])
  (:import (java.util UUID Timer TimerTask Date)
           (org.eclipse.jgit.transport UsernamePasswordCredentialsProvider))
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

(defn update-pull [owner repo pull-request credentials]
  ;; TODO: Clone every time?
  (let [repo (:repo (git/git-clone-full (str "https://github.com/" owner "/" repo ".git")
                                        (str "./tmp/" (UUID/randomUUID) owner "/" repo)))
        head-branch (:ref (:head pull-request))]
    (println "Cloned repo to" (.getPath (.getDirectory (.getRepository repo))))
    (git/git-fetch repo "origin")
    (git/git-checkout repo head-branch true false (str "origin/" head-branch))
    (git/git-rebase repo "origin/master")
    ; git/with-credentials didn't seem to work so using JGit here directly instead.
    (-> repo
        (.push)
        (.setRemote "origin")
        (.setForce true)
        (.setCredentialsProvider (UsernamePasswordCredentialsProvider. (:username credentials) (:password credentials)))
        (.call))))

(defn merge-pull-requests []
  (println "Checking pull requests...")
  (let [pull-requests (pull-requests-to-update (pulls/pulls "sdduursma" "github-merge-bot-test"))
        credentials {:username (System/getenv "GITHUB_MERGE_BOT_USERNAME")
                     :password (System/getenv "GITHUB_MERGE_BOT_PASSWORD")}]
    (doseq [pr pull-requests]
      (update-pull "sdduursma" "github-merge-bot-test" pr credentials))))

(defn -main
  [& args]
  (let [timer-task (proxy [TimerTask] []
                     (run []
                       (merge-pull-requests)))]
    (.scheduleAtFixedRate (Timer.) timer-task 0 30000)))
