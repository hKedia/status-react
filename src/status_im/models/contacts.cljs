(ns status-im.models.contacts
  (:require [status-im.utils.fx :as fx]))

(fx/defn load-contacts
  [{:keys [db all-contacts]}]
  (let [contacts-list (map #(vector (:whisper-identity %) %) all-contacts)
        contacts (into {} contacts-list)]
    {:db (update db :contacts/contacts #(merge contacts %))}))
