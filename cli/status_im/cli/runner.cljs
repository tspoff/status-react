(ns status-im.cli.runner
  (:require [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [cljs.test]
            [status-im.cli.core :as core]))

(enable-console-print!)

(def cli-options
  ;; An option with a required argument
  [["-m MESSAGE" "--message MESSAGE" "The message to send"
    :default "test message"
    :validate [(complement string/blank?) "Can't be a blank string"]]
   ["-p CHAT_NAME" "--public-chat CHAT_NAME" "The name of the public chat"
    :validate [(complement string/blank?) "Can't be a blank string"]]
   ["-r RECEIVER" "--receiver RECEIVER" "The public key of the 1-to-1 contact"]
   ["-c COUNT" "--count COUNT" "The number of messages to send"
    :default 1
    :parse-fn #(js/parseInt %)
    :validate [#(< 0 % 10000) "Must be between 0 and 10000"]]
   ;; A boolean option defaulting to nil
   ["-h" "--help"]])

(def contact-code "0x044adb23f4e1fa51b2f21895442d1194165e08a0e60709b2d564d7d879a1ca6ba363ec79fe5ef0d683689aa1ecdb876a12675ef768d27229ca7f7ca10d937a07f3")
(defn -main [& args]
  (let [{:keys [options] :as all} (cli/parse-opts args cli-options)]
    (core/send options)))

(set! *main-cli-fn* -main)
