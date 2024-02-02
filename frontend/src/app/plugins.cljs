;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.plugins
  "RPC for plugins runtime."
  (:require
   [app.util.timers :as tm]
   [app.common.exceptions :as ex]))

(defonce channel
  (js/BroadcastChannel. "penpot:plugins"))

(defn on-request
  [event]
  (let [data (unchecked-get event "data")]
    (case data
      "ping" (.postMessage channel "pong")
      nil)))

(.addEventListener channel "message" on-request)

(tm/schedule 2000 #(.postMessage channel "initialized"))


