(ns status-im.cli.core
  (:require [cljs.test :refer-macros [deftest is testing async ]]
            [cljs.nodejs :as nodejs]
            re-frame.db
            [status-im.transport.core :as transport]
            [status-im.protocol.handlers :as transport.handlers]
            status-im.ui.screens.events
            [status-im.utils.contacts :as utils.contacts]
            [status-im.transport.shh :as transport.shh]
            [re-frame.core :as rf]
            [status-im.transport.message.core :as transport.message]
            [cljs.core.async :as async]
            [status-im.transport.utils :as transport.utils]
            [day8.re-frame.test :refer-macros [run-test-async run-test-sync] :as rf-test]
            [status-im.test.protocol.node :as node]
            [status-im.transport.message.v1.contact :as transport.contact]
            [status-im.test.protocol.utils :as utils]))

;; NOTE(oskarth): All these tests are evaluated in NodeJS

(nodejs/enable-util-print!)

(def ^:dynamic *message* "normal message")
(def atom-message (atom "normal message"))

(def contact-whisper-identity "0x048f7d5d4bda298447bbb5b021a34832509bd1a8dbe4e06f9b7223d00a59b6dc14f6e142b21d3220ceb3155a6d8f40ec115cd96394d3cc7c55055b433a1758dc74")
(def rpc-url (aget nodejs/process.env "WNODE_ADDRESS"))

(def Web3 (js/require "web3"))
(defn make-web3 []
  (Web3. (Web3.providers.HttpProvider. rpc-url)))

(defn create-keys [shh]
  (let [keypair-promise (.newKeyPair shh)]
    (.getPublicKey shh keypair-promise)))

(def contact-code "0x04aa85f08af4b503b88a1f0a51058e97dca90d074d2ab2669fa4de613b16b42f937ce855c50928870b3349fe50a44c19b2a5238fb9860bc791cb07d7ff9fdaf3a3")

(deftest test-send-message!
  (testing "send contact request & message"
    (run-test-async
      (let [web3          (make-web3)
            shh  (transport.utils/shh web3)
            from          (create-keys shh)
            to-pk         (create-keys shh)
            to-address    (str "0x" (utils.contacts/public-key->address to-pk))]
        (rf/reg-fx :data-store/save-chat (constantly nil))
        (rf/reg-fx :data-store/save-message (constantly nil))
        (rf/reg-fx :data-store/save-contact (constantly nil))
        (rf/reg-fx :data-store.transport/save (constantly nil))
        (rf/reg-fx :data-store/update-message (constantly nil))
        (rf/reg-cofx :data-store/transport #(assoc % :data-store/transport {}))
        (rf/reg-cofx ::transport.handlers/get-web3 #(assoc % :web3 web3))
        (reset! re-frame.db/app-db {:web3 web3
                                    :accounts/accounts {to-address
                                                        {:name "To name"
                                                         :address to-address
                                                         :photo-path "none"
                                                         :signing-phrase "test"
                                                         :public-key to-pk}}
                                    :current-public-key to-pk})

        (rf/dispatch [:initialize-protocol to-address rpc-url])
        (rf/dispatch [:open-chat-with-contact {:whisper-identity contact-code}])
        (rf-test/wait-for [::transport.contact/send-new-sym-key]
                          (doseq [i (range 200)]
                            (rf/dispatch-sync [:set-chat-input-text (str @atom-message  " " i)])
                            (rf/dispatch-sync [:send-current-message]))
                          (rf-test/wait-for [:update-message-status :protocol/send-status-message-error]
                                            (is true)))))))
