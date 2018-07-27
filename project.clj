(defproject github-merge-bot "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [irresponsible/tentacles "0.6.2"]
                 [org.clojure/tools.logging "0.4.1"]]
  :main ^:skip-aot github-merge-bot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
