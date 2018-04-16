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
   ["-g GROUP_CHAT" "--group-chat CHAT_NAME" "The name of the group chat"
    :validate [(complement string/blank?) "Can't be a blank string"]]
   ["-m MEMBERS" "--members MEMBERS", "The members of the group chat"
    :parse-fn #(string/split % ",")
    :validate [(complement string/blank?) "Can't be a blank string"]]
   ["-r RECEIVER" "--receiver RECEIVER" "The public key of the 1-to-1 contact"]
   ["-c COUNT" "--count COUNT" "The number of messages to send"
    :default 1
    :parse-fn #(js/parseInt %)
    :validate [#(< 0 % 10000) "Must be between 0 and 10000"]]
   ;; A boolean option defaulting to nil
   ["-h" "--help"]])

(defn -main [& args]
  (let [{:keys [options] :as all} (cli/parse-opts args cli-options)]
    (core/send options)))

(set! *main-cli-fn* -main)
