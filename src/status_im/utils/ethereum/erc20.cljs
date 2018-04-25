(ns status-im.utils.ethereum.erc20
  "
  Helper functions to interact with [ERC20](https://github.com/ethereum/EIPs/blob/master/EIPS/eip-20-token-standard.md) smart contract

  Example

  Contract: https://ropsten.etherscan.io/address/0x29b5f6efad2ad701952dfde9f29c960b5d6199c5#readContract
  Owner: https://ropsten.etherscan.io/token/0x29b5f6efad2ad701952dfde9f29c960b5d6199c5?a=0xa7cfd581060ec66414790691681732db249502bd

  With a running node on Ropsten:
  (let [web3 (:web3 @re-frame.db/app-db)
        contract \"0x29b5f6efad2ad701952dfde9f29c960b5d6199c5\"
        address \"0xa7cfd581060ec66414790691681732db249502bd\"]
    (erc20/balance-of web3 contract address println))

  => 29166666
  "
  (:require [status-im.utils.ethereum.core :as ethereum]
            [status-im.native-module.core :as status]
            [status-im.utils.ethereum.tokens :as tokens])
  (:refer-clojure :exclude [name symbol]))

(defn name [web3 contract cb]
  (ethereum/call web3 (ethereum/call-params contract "name()") cb))

(defn symbol [web3 contract cb]
  (ethereum/call web3 (ethereum/call-params contract "symbol()") cb))

(defn decimals [web3 contract cb]
  (ethereum/call web3 (ethereum/call-params contract "decimals()") cb))

(defn total-supply [web3 contract cb]
  (ethereum/call web3
                 (ethereum/call-params contract "totalSupply()")
                 #(cb %1 (ethereum/hex->bignumber %2))))

(defn balance-of [web3 contract address cb]
  (ethereum/call web3
                 (ethereum/call-params contract "balanceOf(address)" (ethereum/normalized-address address))
                 #(cb %1 (ethereum/hex->bignumber %2))))

(defn transfer [web3 contract from address value params cb]
  (ethereum/send-transaction web3
                 (merge (ethereum/call-params contract "transfer(address,uint256)" (ethereum/normalized-address address) (ethereum/int->hex value))
                   {:from from}
                   params)
                 #(cb %1 (ethereum/hex->boolean %2))))

(defn transfer-from [web3 contract from-address to-address value cb]
  (ethereum/call web3
                 (ethereum/call-params contract "transferFrom(address,address,uint256)" (ethereum/normalized-address from-address) (ethereum/normalized-address to-address) (ethereum/int->hex value))
                 #(cb %1 (ethereum/hex->boolean %2))))

(defn approve [web3 contract address value cb]
  (ethereum/call web3
                 (ethereum/call-params contract "approve(address,uint256)" (ethereum/normalized-address address)  (ethereum/int->hex value))
                 #(cb %1 (ethereum/hex->boolean %2))))

(defn allowance [web3 contract owner-address spender-address cb]
  (ethereum/call web3
                 (ethereum/call-params contract "allowance(address,address)" (ethereum/normalized-address owner-address) (ethereum/normalized-address spender-address))
                 #(cb %1 (ethereum/hex->bignumber %2))))

(defn- parse-json
  ;; NOTE(dmitryn) Expects JSON response like:
  ;; {"error": "msg"} or {"result": true}
  [s]
  (try
    (let [res (-> s
                  js/JSON.parse
                  (js->clj :keywordize-keys true))]
      ;; NOTE(dmitryn): AddPeer() may return {"error": ""}
      ;; assuming empty error is a success response
      ;; by transforming {"error": ""} to {:result true}
      (if (and (:error res)
               (= (:error res) ""))
        {:result true}
        res))
    (catch :default e
      {:error (.-message e)})))

(defn- add-padding [address]
  (if address
    (str "0x000000000000000000000000" (subs address 2))))

(defn- remove-padding [topic]
  (if topic
    (str "0x" (subs topic 26))))

(defn- parse-transaction-entry [chain direction entries]
  (into {}
        (for [entry entries]
          [(:transactionHash entry)
           {:block     (-> entry :blockNumber)
            :hash      (:transactionHash entry)
            :symbol    (->> entry :address (tokens/address->token chain) :symbol)
            :from      (-> entry :topics second remove-padding)
            :to        (-> entry :topics last remove-padding)
            :value     (-> entry :data ethereum/hex->bignumber)
            :type      direction

            :gas-price 0
            :nonce     0
            :data      "0x"

            :gas-limit 0
            :timestamp 0

            :gas-used  0}])))

(defn- response-handler [chain direction error-fn success-fn]
  (fn handle-response
    ([response]
     (let [{:keys [error result]} (parse-json response)]
       (handle-response error result)))
    ([error result]
     (if error
       (error-fn error)
       (success-fn (parse-transaction-entry chain direction result))))))

(defn get-token-transactions
  ;; TODO(goranjovic): here we cannot use web3 since events don't work with infura
  [network contracts direction address cb]
  (let [chain (ethereum/network->chain-keyword network)
        [from to] (if (= :inbound direction)
                    [nil (ethereum/normalized-address address)]
                    [(ethereum/normalized-address address) nil])
        args {:jsonrpc "2.0"
              :id      2
              :method  "eth_getLogs"
              :params  [{:address   contracts
                         :fromBlock "0x0"
                         :topics    ["0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
                                     (add-padding from)
                                     (add-padding to)]}]}
        payload (.stringify js/JSON (clj->js args))]
    (status/call-web3-private payload
                              (response-handler chain direction js/alert cb))))

(defn get-block-info [web3 number cb]
  (.getBlock (.-eth web3) number (fn [error result]
                                    (if (seq error)
                                      (js/alert (.stringify js/JSON error))
                                      (cb (js->clj result :keywordize-keys true))))))

(defn get-transaction [web3 number cb]
  (.getTransaction (.-eth web3) number (fn [error result]
                                   (if (seq error)
                                     (js/alert (.stringify js/JSON error))
                                     (cb (js->clj result :keywordize-keys true))))))

(defn get-transaction-receipt [web3 number cb]
  (.getTransactionReceipt (.-eth web3) number (fn [error result]
                                                (if (seq error)
                                                  (js/alert (.stringify js/JSON error))
                                                  (cb (js->clj result :keywordize-keys true))))))