;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.plugins
  "RPC for plugins runtime."
  (:require
   [app.common.record :as crc]
   [app.main.store :as st]
   [app.util.timers :as tm]
   [app.common.exceptions :as ex]))

(deftype FileRef [$id])

(crc/define-properties!
  FileRef
  {:name "id"
   :get (fn []
          (this-as self
            (str (unchecked-get self "$id"))))})


(defn ^:export getCurrentFile
  []
  (when-let [file-id (:current-file-id @st/state)]
    (FileRef. file-id)))
