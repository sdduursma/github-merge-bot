(ns github-merge-bot.core
  (:require [tentacles.pulls :as pulls]
            [clj-jgit.porcelain :as git])
  (:import (java.util UUID Timer TimerTask Date)
           (org.eclipse.jgit.transport UsernamePasswordCredentialsProvider))
  (:gen-class))

(defn has-label [pull-request]
  (contains? (set (map :name (:labels pull-request)))
             "LGTM"))

(defn mergeable? [owner repo pull-id]
  (:mergeable (pulls/specific-pull owner repo pull-id)))

(defn pull-request-to-update
  "Pull request to update with its base branch."
  [owner repo pull-requests]
  ; TODO: Is `last` correct here?
  (last (filter #(and (has-label %)
                      (mergeable? owner repo (:number %))) (sort-by :created-at pull-requests))))

(defn update-pull [owner repo pull-request credentials]
  ;; TODO: Clone every time?
  (println "Updating pull request" (:number pull-request) "by rebasing its head branch on master...")
  (let [repo (:repo (git/git-clone-full (str "https://github.com/" owner "/" repo ".git")
                                        (str "./tmp/" (UUID/randomUUID) owner "/" repo)))
        head-branch (:ref (:head pull-request))]
    (println "Cloned repo to" (.getPath (.getDirectory (.getRepository repo))))
    (git/git-fetch repo "origin")
    (git/git-checkout repo head-branch true false (str "origin/" head-branch))
    ; clj-jgit.porcelain/git-rebase hasn't been implemented yet so using JGit here directly instead.
    (-> repo .rebase (.setUpstream "origin/master") .call)
    ; clj-jgit.porcelain/with-credentials didn't seem to work so using JGit here directly instead.
    (-> repo
        (.push)
        (.setRemote "origin")
        (.setForce true)
        (.setCredentialsProvider (UsernamePasswordCredentialsProvider. (:username credentials) (:password credentials)))
        (.call))))

(defn merge-pull-requests []
  (println "Checking pull requests...")
  (let [owner (System/getenv "GITHUB_MERGE_BOT_OWNER")
        repo (System/getenv "GITHUB_MERGE_BOT_REPO")
        credentials {:username (System/getenv "GITHUB_MERGE_BOT_USERNAME")
                     :password (System/getenv "GITHUB_MERGE_BOT_PASSWORD")}]
    (if-let [pull-request (pull-request-to-update owner repo (pulls/pulls owner repo))]
      (update-pull "sdduursma" "github-merge-bot-test" pull-request credentials)
      (println "No pull requests to update."))))

(defn -main
  [& args]
  (let [timer-task (proxy [TimerTask] []
                     (run []
                       (merge-pull-requests)))]
    (.scheduleAtFixedRate (Timer.) timer-task 0 30000)))
