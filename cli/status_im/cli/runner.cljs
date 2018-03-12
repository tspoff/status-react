(ns status-im.cli.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [cljs.test]
            [status-im.cli.core :as core]))

(enable-console-print!)

;; Or doo will exit with an error, see:
;; https://github.com/bensu/doo/issues/83#issuecomment-165498172
(set! (.-error js/console) (fn [x] (.log js/console x)))

(set! goog.DEBUG false)

(println "OUTSIDE MAIN")
(defn -main [& args]
  (println "IN UNNER MAIN" args)
  (binding [status-im.cli.core/*message* "test message"]
    (reset! status-im.cli.core/atom-message "test message")
    (println "MESSAGE" status-im.cli.core/*message*)
    (cljs.test/run-tests 'status-im.cli.core)))

(set! *main-cli-fn* -main)
