(ns status-im.extensions.core
  (:require [clojure.string :as string]
            [pluto.reader :as reader]
            [pluto.registry :as registry]
            [pluto.storages :as storages]
            [re-frame.core :as re-frame]
            [status-im.accounts.update.core :as accounts.update]
            [status-im.chat.commands.core :as commands]
            [status-im.chat.commands.impl.transactions :as transactions]
            [status-im.i18n :as i18n]
            [status-im.ui.components.react :as react]
            [status-im.utils.fx :as fx]))

(def components
  {'view           {:value react/view}
   'text           {:value react/text}
   'nft-token      {:value transactions/nft-token}
   'send-status    {:value transactions/send-status}
   'asset-selector {:value transactions/choose-nft-asset-suggestion}
   'token-selector {:value transactions/choose-nft-token-suggestion}})

(def app-hooks #{commands/command-hook})

(def capacities
  (reduce (fn [capacities hook]
            (assoc-in capacities [:hooks :commands] hook))
          {:components components
           :queries    {'get-collectible-token {:value :get-collectible-token}}
           :events     {}}
          app-hooks))

(defn read-extension [o]
  (-> o :value first :content reader/read))

(defn parse [{:keys [data] :as m}]
  (try
    (let [{:keys [errors] :as extension-data} (reader/parse {:capacities capacities} data)]
      (when errors
        (println "Failed to parse status extensions" errors))
      extension-data)
    (catch :default e (println "EXC" e))))

(defn url->uri [s]
  (when s
    (string/replace s "https://get.status.im/extension/" "")))

(defn load-from [url f]
  (when-let [uri (url->uri url)]
    (storages/fetch uri f)))

(fx/defn set-input
  [{:keys [db]} input-key value]
  {:db (update db :extensions/manage assoc input-key {:value value})})

(fx/defn fetch [cofx id]
  (get-in cofx [:db :account/account :extensions id]))

(fx/defn edit
  [cofx id]
  (let [{:keys [url]} (fetch cofx id)]
    (-> (set-input cofx :url (str url))
        (assoc :dispatch [:navigate-to :edit-extension]))))

(fx/defn add
  [cofx extension-data]
  (when-let [extension-key (get-in extension-data ['meta :name])]
    (fx/merge cofx
              #(registry/add extension-data %)
              #(registry/activate extension-key %))))

(fx/defn find
  [{{:extensions/keys [manage] :account/keys [account] :as db} :db
    random-id-generator :random-id-generator :as cofx}
   extension-data]
  (let [extension-key  (get-in extension-data ['meta :name])
        {:keys [url id]} manage
        extension      {:id      (-> (:value id)
                                     (or (random-id-generator))
                                     (string/replace "-" ""))
                        :name    (str extension-key)
                        :url     (:value url)
                        :active? true}
        new-extensions (assoc (:extensions account) (:id extension) extension)]
    (fx/merge cofx
              {:ui/show-confirmation {:title     (i18n/label :t/success)
                                      :content   (i18n/label :t/extension-installed)
                                      :on-accept #(re-frame/dispatch [:navigate-to-clean :my-profile])
                                      :on-cancel nil}}
              (accounts.update/account-update {:extensions new-extensions} {})
              (add extension-data))))

(fx/defn toggle-activation
  [cofx id state]
  (let [toggle-fn      (get {true  registry/activate
                             false registry/deactivate}
                            state)
        extensions     (get-in cofx [:db :account/account :extensions])
        new-extensions (assoc-in extensions [id :active?] state)]
    (fx/merge cofx
              (accounts.update/account-update {:extensions new-extensions} {:success-event nil})
              #(toggle-fn id %))))

(defn load-active-extensions
  [{:keys [db]}]
  (let [extensions (->> (get-in db [:account/account :extensions])
                        vals
                        (filter :active?))]
    (doseq [{:keys [url]} extensions]
      (load-from url #(re-frame/dispatch [:extension/add (-> % read-extension parse :data)])))))

