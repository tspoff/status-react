(ns status-im.cli.core
  (:require [cljs.test :refer-macros [deftest is testing async ]]
            [cljs.nodejs :as nodejs]
            re-frame.db
            [status-im.transport.core :as transport]
            [status-im.protocol.handlers :as transport.handlers]
            [status-im.utils.contacts :as utils.contacts]
            [status-im.transport.shh :as transport.shh]
            [re-frame.core :as rf]
            status-im.ui.screens.events
            [status-im.transport.message.core :as transport.message]
            [cljs.core.async :as async]
            [status-im.transport.utils :as transport.utils]
            [day8.re-frame.test :refer-macros [run-test-async run-test-sync] :as rf-test]
            [status-im.transport.message.v1.contact :as transport.contact]))


(def Web3 (js/require "web3"))
(defn- make-web3 [rpc-url]
  (Web3. (Web3.providers.HttpProvider. rpc-url)))

(defn- add-sym-key-sync
  [{:keys [web3 sym-key on-success on-error]}]
  (let [resp (.. web3
                 -shh
                 (addSymKey sym-key))]
    (on-success resp)))

(defn- new-sym-key-sync
  [{:keys [web3 on-success on-error]}]
  (let [resp (.. web3
                 -shh
                 (newSymKey))]
    (on-success resp)))

(defn- get-sym-key-sync
  [{:keys [web3 sym-key-id on-success on-error]}]
  (let [resp (.. web3
                 -shh
                 (getSymKey sym-key-id))]
    (on-success resp)))

(defn generate-sym-key-from-password-sync
  [{:keys [web3 password on-success on-error]}]
  (let [resp (.. web3
                 -shh
                 (generateSymKeyFromPassword password))]
    (on-success resp)))

(defn- post-message-sync
  [{:keys [web3 whisper-message on-success on-error]}]
  (let [resp (.. web3
                 -shh
                 (post (clj->js whisper-message)))]
    (on-success resp)))

(defn- create-keys [shh]
  (.getPublicKey shh
                 (.newKeyPair shh)))

(defn- stub-data-store! []
  (rf/reg-fx :data-store/save-chat (constantly nil))
  (rf/reg-fx :data-store/save-message (constantly nil))
  (rf/reg-fx :data-store/save-contact (constantly nil))
  (rf/reg-fx :data-store.transport/save (constantly nil))
  (rf/reg-fx :data-store/update-message (constantly nil))
  (rf/reg-fx :data-store/init-store (constantly nil))
  (rf/reg-fx :status-im.ui.screens.events/init-store (constantly nil))
  (rf/reg-cofx :data-store/transport #(assoc % :data-store/transport {})))

(defn- inject-web3! [web3]
  (rf/reg-cofx ::transport.handlers/get-web3 #(assoc % :web3 web3)))

(defn- build-message [text counter append-counter?]
  (if append-counter?
    (str text " " counter)
    text))

(defn send [{:keys [count
                    message
                    receiver
                    public-chat
                    from
                    rpc-url
                    append-counter?]
             :as opts
             :or {message "test"
                  count 1
                  rpc-url "http://localhost:8545"
                  append-counter? true}}]
  (let [web3          (make-web3 rpc-url)
        from-pk       (create-keys (transport.utils/shh web3))
        from-address  (str "0x" (utils.contacts/public-key->address from-pk))]
    (run-test-sync
      (with-redefs [transport.shh/add-sym-key add-sym-key-sync
                    transport.shh/get-sym-key get-sym-key-sync
                    transport.shh/generate-sym-key-from-password generate-sym-key-from-password-sync
                    transport.shh/post-message post-message-sync
                    transport.shh/new-sym-key new-sym-key-sync]
        (stub-data-store!)
        (inject-web3! (make-web3 rpc-url))

        (rf/dispatch [:initialize-db])

        (reset! re-frame.db/app-db {:accounts/accounts {from-address
                                                        {:name "To name"
                                                         :address from-address
                                                         :photo-path "none"
                                                         :signing-phrase "test"
                                                         :public-key from-pk}}
                                    :current-public-key from-pk})

        (rf/dispatch [:initialize-protocol from-address rpc-url])
        (cond
          public-chat (rf/dispatch [:create-new-public-chat public-chat])
          receiver     (rf/dispatch [:open-chat-with-contact {:whisper-identity receiver}]))
        (doseq [i (range count)]
          (println @re-frame.db/app-db)
          (rf/dispatch [:set-chat-input-text (build-message message i opts)])
          (rf/dispatch [:send-current-message]))
        (nodejs/process.exit 0)))))
