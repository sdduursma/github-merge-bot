(ns github-merge-bot.core
  (:require [tentacles.pulls :as pulls]
            [tentacles.core :as tentacles]
            [clj-jgit.porcelain :as git])
  (:import (java.util Timer TimerTask)
           (org.eclipse.jgit.transport UsernamePasswordCredentialsProvider RefSpec)
           (org.eclipse.jgit.revwalk RevWalk)
           (org.eclipse.jgit.revwalk.filter RevFilter)
           (org.eclipse.jgit.lib ObjectId)
           (java.io FileNotFoundException)
           (org.eclipse.jgit.api Git))
  (:gen-class))

(defn github-reviews [owner repo pr-id & [options]]
  (tentacles/api-call :get "repos/%s/%s/pulls/%s/reviews" [owner repo pr-id] options))

(defn github-create-review [owner repo pr-id & [options]]
  (tentacles/api-call :post "repos/%s/%s/pulls/%s/reviews" [owner repo pr-id] options))

(defn approved? [owner repo pull-request]
  (let [reviews (github-reviews owner repo (:number pull-request))
        reviews-by-author (partition-by #(-> % :user :id) reviews)
        approves-by-author (for [reviews reviews-by-author]
                             (->> reviews (map :state) (filter #{"CHANGES_REQUESTED" "APPROVED"}) last (= "APPROVED")))]
    (and
      (not (empty? approves-by-author))
      (every? true? approves-by-author))))

(defn has-label? [pull-request]
  (contains? (set (map :name (:labels pull-request)))
             "LGTM"))

(defn merge-candidate [owner repo pull-requests]
  (last (filter #(has-label? %)
                (sort-by :created-at pull-requests))))

(defn procure-repo
  ([owner repo-name]
   (procure-repo owner repo-name (str "./tmp/" owner "/" repo-name)))
  ([owner repo-name directory]
   (try
     (git/load-repo directory)
     (catch FileNotFoundException _
       (println "Repo not found locally, cloning...")
       (:repo (git/git-clone-full (str "https://github.com/" owner "/" repo-name ".git")
                                  directory))))))

(defn head-up-to-date-with-base? [owner repo pull-request]
  (let [repo (procure-repo owner repo)]
    (git/git-fetch repo "origin")
    (let [rev-walk (RevWalk. (.getRepository repo))
          master (.parseCommit rev-walk (.getObjectId (.findRef (.getRepository repo) "origin/master")))
          _ (.reset rev-walk)
          base (.parseCommit rev-walk (ObjectId/fromString (:sha (:head pull-request))))
          _ (.reset rev-walk)
          merge-base (-> (doto rev-walk (.setRevFilter (RevFilter/MERGE_BASE))
                                        (.markStart [master base]))
                         (.next))]
      (= (.getName merge-base)
         (.getName master)))))

(defn update-pull-request [owner repo-name pull-request credentials]
  (println "Updating pull request" (:number pull-request) "by rebasing its head branch on master...")
  (let [^Git repo (procure-repo owner repo-name)
        head (:sha (:head pull-request))
        approved (approved? owner repo-name pull-request)]
    (git/git-fetch repo "origin")
    (git/git-checkout repo head)
    ; clj-jgit.porcelain/git-rebase hasn't been implemented yet so using JGit here directly instead.
    (-> repo .rebase (.setUpstream "origin/master") .call)
    ; clj-jgit.porcelain/with-credentials didn't seem to work so using JGit here directly instead.
    (-> repo
        (.push)
        (.setRemote "origin")
        (.setRefSpecs [(RefSpec. (str "HEAD:refs/heads/" (:ref (:head pull-request))))])
        (.setForce true)
        (.setCredentialsProvider (UsernamePasswordCredentialsProvider. (:username credentials) (:password credentials)))
        (.call))
    (if approved
      ; TODO: Specify :commit-id when creating review.
      (do (println "Re-approving pull request after updating...")
          (github-create-review owner
                                repo-name
                                (:number pull-request)
                                {:body  "Automatically re-approving after updating this pull request."
                                 :event "APPROVE"})))))

(defn try-merge-pull-request [owner repo pull-request credentials]
  (println (str "Trying to merge pull request #" (:number pull-request) "..."))
  (let [result (pulls/merge owner repo (:number pull-request) {})]
    (if (:merged result)
      (println (str "Successfully merged pull request #" (:number pull-request) "."))
      (println (str "Unable to merge pull request #" (:number pull-request) ": " (:message result))))))

(defn merge-pull-requests []
  (println "Checking pull requests...")
  (let [owner (System/getenv "GITHUB_MERGE_BOT_OWNER")
        repo (System/getenv "GITHUB_MERGE_BOT_REPO")
        credentials {:username (System/getenv "GITHUB_MERGE_BOT_USERNAME")
                     :password (System/getenv "GITHUB_MERGE_BOT_PASSWORD")}]
    (tentacles/with-defaults {:auth (str (:username credentials) ":" (:password credentials))}
      (git/with-credentials (:username credentials) (:password credentials)
        (if-let [pr (merge-candidate owner repo (pulls/pulls owner repo))]
          (if (head-up-to-date-with-base? owner repo pr)
            (try-merge-pull-request owner repo pr credentials)
            (update-pull-request owner repo pr credentials))
          (println "No pull requests found to merge or update."))))))

(defn -main
  [& args]
  (let [timer-task (proxy [TimerTask] []
                     (run []
                       (merge-pull-requests)))]
    (.schedule (Timer.) timer-task 0 15000)))
