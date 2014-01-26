(defproject clobaconjure "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2138"]]
  :plugins [[lein-cljsbuild "1.0.1"]
            [com.cemerick/clojurescript.test "0.2.1"]]
  :cljsbuild {:builds
              [{:id "clobaconjure"
                :source-paths ["src"]
                :compiler {:output-to "target/clobaconjure.js"
                           :optimizations :advanced
                           :pretty-print false}}
               {:id "unit-test"
                :source-paths ["src" "test"]
                :compiler {:output-to "target/unit-test.js"
                           :optimizations :whitespace
                           :pretty-print true}}]
              :test-commands {"unit-test" ["phantomjs" :runner
                                           "target/unit-test.js"]}})
