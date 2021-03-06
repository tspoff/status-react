(ns status-im.ui.screens.accounts.events
  (:require [re-frame.core :as re-frame]
            [taoensso.timbre :as log]
            [status-im.native-module.core :as status]
            [status-im.utils.types :refer [json->clj]]
            [status-im.utils.identicon :refer [identicon]]
            [status-im.utils.random :as random]
            [clojure.string :as str]
            [status-im.utils.datetime :as time]
            [status-im.utils.handlers :as handlers]
            [status-im.utils.handlers-macro :as handlers-macro]
            [status-im.ui.screens.accounts.statuses :as statuses]
            [status-im.utils.signing-phrase.core :as signing-phrase]
            [status-im.utils.gfycat.core :refer [generate-gfy]]
            [status-im.utils.hex :as utils.hex]
            [status-im.constants :as constants]
            [status-im.transport.message.v1.contact :as message.contact]
            [status-im.transport.message.core :as transport]
            status-im.ui.screens.accounts.create.navigation
            [status-im.chat.models :as chat.models]))

;;;; COFX

(re-frame/reg-cofx
  ::get-signing-phrase
  (fn [coeffects _]
    (assoc coeffects :signing-phrase (signing-phrase/generate))))

(re-frame/reg-cofx
  ::get-status
  (fn [coeffects _]
    (assoc coeffects :status (rand-nth statuses/data))))

;;;; FX

(re-frame/reg-fx
  ::create-account
  (fn [password]
    (status/create-account
     password
     #(re-frame/dispatch [::account-created (json->clj %) password]))))

;;;; Handlers

(handlers/register-handler-fx
  :create-account
  (fn [{{:accounts/keys [create] :as db} :db} _]
    {:db (update db :accounts/create assoc :step :account-creating :error nil)
     ::create-account (:password create)}))

(defn add-account
  "Takes db and new account, creates map of effects describing adding account to database and realm"
  [{:keys [network] :networks/keys [networks] :as db} {:keys [address] :as account}]
  (let [enriched-account (assoc account
                                :network  network
                                :networks networks
                                :address  address)]
    {:db           (assoc-in db [:accounts/accounts address] enriched-account)
     :data-store/save-account enriched-account}))

;; TODO(janherich) we have this handler here only because of the tests, refactor/improve tests ASAP
(handlers/register-handler-fx
  :add-account
  (fn [{:keys [db]} [_ new-account]]
    (add-account db new-account)))

(handlers/register-handler-fx
  ::account-created
  [re-frame/trim-v (re-frame/inject-cofx ::get-signing-phrase) (re-frame/inject-cofx ::get-status)]
  (fn [{:keys [signing-phrase status db] :as cofx} [{:keys [pubkey address mnemonic]} password]]
    (let [normalized-address (utils.hex/normalize-hex address)
          account            {:public-key          pubkey
                              :address             normalized-address
                              :name                (generate-gfy pubkey)
                              :status              status
                              :signed-up?          true
                              :photo-path          (identicon pubkey)
                              :signing-phrase      signing-phrase
                              :mnemonic            mnemonic
                              :settings            constants/default-account-settings}]
      (log/debug "account-created")
      (when-not (str/blank? pubkey)
        (-> (add-account db account)
            (assoc :dispatch [:login-account normalized-address password]))))))

(handlers/register-handler-fx
  :load-accounts
  [(re-frame/inject-cofx :data-store/get-all-accounts)]
  (fn [{:keys [db all-accounts]} _]
    (let [accounts (->> all-accounts
                        (map (fn [{:keys [address] :as account}]
                               [address account]))
                        (into {}))
          ;;workaround for realm bug, migrating account v4
          events (mapv #(when (empty? (:networks %)) [:account-update-networks (:address %)]) (vals accounts))]
      (merge
       {:db (assoc db :accounts/accounts accounts)}
       (when-not (empty? events)
         {:dispatch-n events})))))

(handlers/register-handler-fx
  :account-update-networks
  (fn [{{:accounts/keys [accounts] :networks/keys [networks] :as db} :db} [_ id]]
    (let [current-account (get accounts id)
          new-account (assoc current-account :networks networks)]
      {:db           (assoc-in db [:accounts/accounts id] new-account)
       :data-store/save-account new-account})))

(defn update-settings [settings {:keys [db] :as cofx}]
  (let [{:accounts/keys [current-account-id accounts]} db
        new-account                                    (-> (get accounts current-account-id)
                                                           (assoc :settings settings))]
    {:db                      (assoc-in db [:accounts/accounts current-account-id] new-account)
     :data-store/save-account new-account}))

(defn account-update
  "Takes effects (containing :db) + new account fields, adds all effects necessary for account update.
  Optionally, one can specify event to be dispatched after fields are persisted."
  ([new-account-fields cofx]
   (account-update new-account-fields nil cofx))
  ([new-account-fields after-update-event {{:accounts/keys [accounts current-account-id] :as db} :db :as cofx}]
   (let [current-account (get accounts current-account-id)
         new-account     (merge current-account new-account-fields)
         fx              {:db                      (assoc-in db [:accounts/accounts current-account-id] new-account)
                          :data-store/save-account (assoc new-account :after-update-event after-update-event)}
         {:keys [name photo-path]} new-account]
     (if (or (:name new-account-fields) (:photo-path new-account-fields))
       (handlers-macro/merge-fx cofx fx (transport/send (message.contact/ContactUpdate. name photo-path) nil))
       fx))))

(handlers/register-handler-fx
  :send-account-update-if-needed
  (fn [{{:accounts/keys [accounts current-account-id] :as db} :db now :now :as cofx} _]
    (let [{:keys [last-updated]} (get accounts current-account-id)
          needs-update?          (> (- now last-updated) time/week)]
      (log/info "Need to send account-update: " needs-update?)
      (when needs-update?
        ;; TODO(janherich): this is very strange and misleading, need to figure out why it'd necessary to update
        ;; account with network update when last update was more then week ago
        (account-update nil cofx)))))

(handlers/register-handler-fx
  :account-set-name
  (fn [{{:accounts/keys [create] :as db} :db :as cofx} _]
    (handlers-macro/merge-fx cofx
                       {:db       (assoc-in db [:accounts/create :show-welcome?] true)
                        :dispatch [:navigate-to-clean :usage-data [:account-finalized]]}
                       (account-update {:name (:name create)}))))

(handlers/register-handler-fx
  :account-finalized
  (fn [{db :db} _]
    {:db db
     :dispatch-n [[:navigate-to-clean :home]
                  [:request-notifications]]}))

(handlers/register-handler-fx
  :account-set-input-text
  (fn [{db :db} [_ input-key text]]
    {:db (update db :accounts/create merge {input-key text :error nil})}))

(handlers/register-handler-fx
  :update-sign-in-time
  (fn [{db :db now :now :as cofx} _]
    (account-update {:last-sign-in now} cofx)))

(handlers/register-handler-fx
  :reset-account-creation
  (fn [{db :db} _]
    {:db (update db :accounts/create assoc :step :enter-password :password nil :password-confirm nil :error nil)}))

(handlers/register-handler-fx
  :switch-dev-mode
  (fn [cofx [_ dev-mode]]
    (account-update {:dev-mode? dev-mode} cofx)))
