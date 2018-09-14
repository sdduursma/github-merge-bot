(ns github-merge-bot.core-test
  (:require [clojure.test :refer :all]
            [github-merge-bot.core :refer :all]
            [clj-jgit.porcelain :as git])
  (:import (java.util UUID)
           (org.eclipse.jgit.api Git)))

(defn pull-request-for-repo-branch [^Git origin-repo branch-name]
  {:number 1
   :head {:sha (.getName (.getObjectId (.findRef (.getRepository origin-repo) branch-name)))
          :ref branch-name}})

(deftest test-approved?
  (with-redefs [github-reviews (constantly [])]
    (is (not (approved? "foo" "bar" {:id 42}))))

  (with-redefs [github-reviews (constantly [{:state "APPROVED" :user {:id 1}}])]
    (is (approved? "foo" "bar" {:id 42 })))

  (with-redefs [github-reviews (constantly [{:state "APPROVED" :user {:id 1}}
                                            {:state "COMMENTED" :user {:id 1}}])]
    (is (approved? "foo" "bar" {:id 42})))

  (with-redefs [github-reviews (constantly [{:state "APPROVED" :user {:id 1}}
                                            {:state "CHANGES_REQUESTED" :user {:id 1}}])]
    (is (not (approved? "foo" "bar" {:id 42}))))

  (with-redefs [github-reviews (constantly [{:state "CHANGES_REQUESTED" :user {:id 1}}
                                            {:state "APPROVED" :user {:id 1}}])]
    (is (approved? "foo" "bar" {:id 42})))

  (with-redefs [github-reviews (constantly [{:state "CHANGES_REQUESTED" :user {:id 1}}
                                            {:state "APPROVED" :user {:id 2}}])]
    (is (not (approved? "foo" "bar" {:id 42}))))

  (with-redefs [github-reviews (constantly [{:state "CHANGES_REQUESTED" :user {:id 1}}
                                            {:state "APPROVED" :user {:id 1}}
                                            {:state "APPROVED" :user {:id 2}}])]
    (is (approved? "foo" "bar" {:id 42}))))

(deftest test-update-pull-request
  (let [origin-repo-dir (str (System/getProperty "java.io.tmpdir") (UUID/randomUUID))
        origin-repo (git/git-init origin-repo-dir)
        rev-a (git/git-commit origin-repo "A")
        _ (git/git-commit origin-repo "B")
        _ (git/git-checkout origin-repo "feature-c" true false (.getName (.getId rev-a)))
        _ (git/git-commit origin-repo "C")
        _ (git/git-checkout origin-repo "master")
        repo (:repo (git/git-clone-full origin-repo-dir (str (System/getProperty "java.io.tmpdir") (UUID/randomUUID))))]
    (with-redefs [procure-repo (constantly repo)]
      (update-pull-request "foo" "bar" (pull-request-for-repo-branch origin-repo "feature-c") {:username "foo" :password "abc"})
      (is (head-up-to-date-with-base? "foo" "bar" (pull-request-for-repo-branch origin-repo "feature-c"))))))
