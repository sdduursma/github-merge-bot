(defproject github-merge-bot "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-tools-deps "0.4.1"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :main ^:skip-aot github-merge-bot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
