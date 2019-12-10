;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2015-2017 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.main.data.projects
  (:require
   [cljs.spec.alpha :as s]
   [cuerdas.core :as str]
   [beicon.core :as rx]
   [potok.core :as ptk]
   [uxbox.main.repo.core :as rp]
   [uxbox.main.data.pages :as udp]
   [uxbox.util.uuid :as uuid]
   [uxbox.util.spec :as us]
   [uxbox.util.time :as dt]
   [uxbox.util.router :as rt]))

;; --- Specs

(s/def ::id uuid?)
(s/def ::name string?)
(s/def ::version integer?)
(s/def ::user-id uuid?)
(s/def ::created-at inst?)
(s/def ::modified-at inst?)

(s/def ::project-entity
  (s/keys ::req-un [::id
                    ::name
                    ::version
                    ::user-id
                    ::created-at
                    ::modified-at]))

(declare fetch-projects)
(declare projects-fetched?)

;; --- Helpers

(defn assoc-project
  "A reduce function for assoc the project to the state map."
  [state {:keys [id] :as project}]
  (s/assert ::project-entity project)
  (update-in state [:projects id] merge project))

(defn dissoc-project
  "A reduce function for dissoc the project from the state map."
  [state id]
  (update state :projects dissoc id))

;; --- Initialize

(declare fetch-files)
(declare initialized)

(defn initialize
  [id]
  (ptk/reify ::initialize
    ptk/UpdateEvent
    (update [_ state]
      (update state :dashboard-projects assoc :id id))

    ptk/WatchEvent
    (watch [_ state stream]
      (rx/merge
       (rx/of (fetch-files id))
       (->> stream
            (rx/filter (ptk/type? ::files-fetched))
            (rx/take 1)
            (rx/map #(initialized id (deref %))))))))

(defn initialized
  [id files]
  (ptk/reify ::initialized
    ptk/UpdateEvent
    (update [_ state]
      (let [files (into #{} (map :id) files)]
        (update-in state [:dashboard-projects :files] assoc id files)))))

;; --- Update Opts (Filtering & Ordering)

(defn update-opts
  [& {:keys [order filter] :as opts}]
  (ptk/reify ::update-opts
    ptk/UpdateEvent
    (update [_ state]
      (update state :dashboard-projects merge
              (when order {:order order})
              (when filter {:filter filter})))))

;; --- Projects Fetched

(defn projects-fetched
  [projects]
  (s/assert (s/every ::project-entity) projects)
  (ptk/reify ::projects-fetched
    ptk/UpdateEvent
    (update [_ state]
      (reduce assoc-project state projects))))

(defn projects-fetched?
  [v]
  (= ::projects-fetched  (ptk/type v)))

;; --- Fetch Projects

(def fetch-projects
  (ptk/reify ::fetch-projects
    ptk/WatchEvent
    (watch [_ state stream]
      (->> (rp/query :projects)
           (rx/map projects-fetched)))))

;; --- Fetch Files

(declare files-fetched)

(defn fetch-files
  [project-id]
  (ptk/reify ::fetch-files
    ptk/WatchEvent
    (watch [_ state stream]
      (->> (rp/query :project-files {:project-id project-id})
           (rx/map files-fetched)))))

;; --- Fetch File (by ID)

(defn fetch-file
  [id]
  (s/assert ::us/uuid id)
  (ptk/reify ::fetch-file
    ptk/WatchEvent
    (watch [_ state stream]
      (->> (rp/query :project-file {:id id})
           (rx/map #(files-fetched [%]))))))

;; --- Files Fetched

(s/def ::files any?)

(defn files-fetched
  [files]
  (s/assert ::files files)
  (ptk/reify ::files-fetched
    cljs.core/IDeref
    (-deref [_] files)

    ptk/UpdateEvent
    (update [_ state]
      (let [assoc-file #(assoc-in %1 [:files (:id %2)] %2)]
        (reduce assoc-file state files)))))

;; --- Project Persisted

(defrecord ProjectPersisted [data]
  ptk/UpdateEvent
  (update [_ state]
    (assoc-project state data)))

(defn project-persisted
  [data]
  {:pre [(map? data)]}
  (ProjectPersisted. data))

;; --- Persist Project

(defrecord PersistProject [id]
  ptk/WatchEvent
  (watch [_ state stream]
    (let [project (get-in state [:projects id])]
      (->> (rp/mutation :update-project project)
           (rx/map project-persisted)))))

(defn persist-project
  [id]
  {:pre [(uuid? id)]}
  (PersistProject. id))

;; --- Rename Project

(defrecord RenameProject [id name]
  ptk/UpdateEvent
  (update [_ state]
    (assoc-in state [:projects id :name] name))

  ptk/WatchEvent
  (watch [_ state stream]
    (rx/of (persist-project id))))

(defn rename-project
  [id name]
  {:pre [(uuid? id) (string? name)]}
  (RenameProject. id name))

;; --- Delete Project (by id)

(defrecord DeleteProject [id]
  ptk/WatchEvent
  (watch [_ state s]
    (letfn [(on-success [_]
              #(dissoc-project % id))]
      (->> (rp/mutation :delete-project {:id id})
           (rx/map on-success)))))

(defn delete-project
  [id]
  (if (map? id)
    (DeleteProject. (:id id))
    (DeleteProject. id)))

;; --- Create Project

(declare project-created)

(s/def ::create-project
  (s/keys :req-un [::name]))

(defn create-project
  [{:keys [name] :as params}]
  (s/assert ::create-project params)
  (ptk/reify ::create-project
    ptk/WatchEvent
    (watch [this state stream]
      (->> (rp/mutation :create-project {:name name})
           (rx/map project-created)))))

;; --- Project Created

(defn project-created
  [data]
  (ptk/reify ::project-created
    ptk/UpdateEvent
    (update [_ state]
      (assoc-project state data))))

;; --- Go To Project

(defn go-to
  [file-id]
  (s/assert ::us/uuid file-id)
  (ptk/reify ::go-to
    ptk/WatchEvent
    (watch [_ state stream]
      (let [page-ids (get-in state [:files file-id :pages])]
        (let [path-params {:file-id file-id}
              query-params {:page-id (first page-ids)}]
          (rx/of (rt/nav :workspace path-params query-params)))))))
