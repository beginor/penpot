;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.main.ui.workspace.viewport.selection
  "Selection handlers component."
  (:require
   [app.common.data.macros :as dm]
   [app.common.files.helpers :as cfh]
   [app.common.geom.matrix :as gmt]
   [app.common.geom.point :as gpt]
   [app.common.geom.shapes :as gsh]
   [app.common.types.container :as ctn]
   [app.common.types.shape :as cts]
   [app.main.data.workspace :as dw]
   [app.main.refs :as refs]
   [app.main.store :as st]
   [app.main.ui.context :as ctx]
   [app.main.ui.css-cursors :as cur]
   [app.main.ui.workspace.shapes.path.editor :refer [path-editor]]
   [app.util.debug :as dbg]
   [app.util.dom :as dom]
   [app.util.object :as obj]
   [app.util.array :as array]
   [rumext.v2 :as mf]
   [rumext.v2.util :refer [map->obj]]))

(def rotation-handler-size 20)
(def resize-point-radius 4)
(def resize-point-circle-radius 10)
(def resize-point-rect-size 8)
(def resize-side-height 8)
(def selection-rect-color-normal "var(--color-select)")
(def selection-rect-color-component "var(--color-component-highlight)")
(def selection-rect-width 1)
(def min-selrect-side 10)
(def small-selrect-side 30)

(mf/defc ^:private selection-rect
  {::mf/wrap-props false}
  [{:keys [transform rect zoom color on-move-selected on-context-menu]}]
  (let [x (dm/get-prop rect :x)
        y (dm/get-prop rect :y)
        w (dm/get-prop rect :width)
        h (dm/get-prop rect :height)]
    [:rect.main.viewport-selrect
     {:x x
      :y y
      :width w
      :height h
      :transform (dm/str transform)
      :on-pointer-down on-move-selected
      :on-context-menu on-context-menu
      :style {:stroke color
              :stroke-width (/ selection-rect-width zoom)
              :fill "none"}}]))

(defn- handlers-for-selection
  [selrect shape zoom]
  (let [x      (dm/get-prop selrect :x)
        y      (dm/get-prop selrect :y)
        width  (dm/get-prop selrect :width)
        height (dm/get-prop selrect :height)
        type   (dm/get-prop shape :type)

        threshold-small (/ 25 zoom)
        threshold-tiny (/ 10 zoom)

        small-width? (<= width threshold-small)
        tiny-width?  (<= width threshold-tiny)

        small-height? (<= height threshold-small)
        tiny-height?  (<= height threshold-tiny)

        path-shape?   (cfh/path-shape? shape)

        vertical-line? (and path-shape? tiny-width?)
        horizontal-line? (and path-shape? tiny-height?)

        align (if (or small-width? small-height?)
                :outside
                :inside)

        base #js [#js {:type :rotation
                       :position :top-left
                       :props #js {:cx x :cy y}}

                  #js {:type :rotation
                       :position :top-right
                       :props #js {:cx (+ x width) :cy y}}

                  #js {:type :rotation
                       :position :bottom-right
                       :props #js {:cx (+ x width) :cy (+ y height)}}

                  #js {:type :rotation
                       :position :bottom-left
                       :props #js {:cx x :cy (+ y height)}}]]

    (cond-> base
      (not ^boolean horizontal-line?)
      (-> (array/conj!
           #js {:type :resize-side
                :position :top
                :props #js {:x (if small-width? (+ x (/ (- width threshold-small) 2)) x)
                            :y y
                            :length (if small-width? threshold-small width)
                            :angle 0
                            :align align
                            :show-handler? tiny-width?}})
          (array/conj!
           #js {:type :resize-side
                :position :bottom
                :props #js {:x (if small-width? (+ x (/ (+ width threshold-small) 2)) (+ x width))
                            :y (+ y height)
                            :length (if small-width? threshold-small width)
                            :angle 180
                            :align align
                             :show-handler? tiny-width?}}))

      (not ^boolean vertical-line?)
      (-> (array/conj!
           #js {:type :resize-side
               :position :right
                :props #js {:x (+ x width)
                            :y (if small-height? (+ y (/ (- height threshold-small) 2)) y)
                            :length (if small-height? threshold-small height)
                            :angle 90
                            :align align
                            :show-handler? tiny-height?}})
          (array/conj!
           #js {:type :resize-side
                :position :left
                :props #js {:x x
                            :y (if small-height? (+ y (/ (+ height threshold-small) 2)) (+ y height))
                            :length (if small-height? threshold-small height)
                            :angle 270
                            :align align
                            :show-handler? tiny-height?}}))

      (and (not ^boolean tiny-width?)
           (not ^boolean tiny-height?))
      (-> (array/conj! #js {:type :resize-point
                            :position :top-left
                            :props #js {:cx x :cy y :align align}})
          (array/conj! #js {:type :resize-point
                            :position :top-right
                            :props #js {:cx (+ x width) :cy y :align align}})
          (array/conj! #js {:type :resize-point
                            :position :bottom-right
                            :props #js {:cx (+ x width) :cy (+ y height) :align align}})
          (array/conj! #js {:type :resize-point
                            :position :bottom-left
                            :props #js {:cx x :cy (+ y height) :align align}})))))

(mf/defc rotation-handler
  {::mf/wrap-props false}
  [{:keys [cx cy transform position rotation zoom on-rotate]}]
  (let [size  (/ rotation-handler-size zoom)
        x     (- cx (if (or (= position :top-left)
                            (= position :bottom-left))
                      size
                      0))
        y     (- cy (if (or (= position :top-left)
                            (= position :top-right))
                      size
                      0))

        angle (case position
                :top-left 0
                :top-right 90
                :bottom-right 180
                :bottom-left 270)]
    [:rect {:x x
            :y y
            :class (cur/get-dynamic "rotate" (+ rotation angle))
            :width size
            :height size
            :fill (if (dbg/enabled? :handlers) "blue" "none")
            :stroke-width 0
            :transform (dm/str transform)
            :on-pointer-down on-rotate}]))

(mf/defc resize-point-handler
  {::mf/wrap-props false}
  [{:keys [cx cy zoom position on-resize transform rotation color align]}]
  (let [layout     (mf/deref refs/workspace-layout)
        scale-text (:scale-text layout)
        cursor     (if (or (= position :top-left)
                           (= position :bottom-right))
                     (if ^boolean scale-text
                       (cur/get-dynamic "scale-nesw" rotation)
                       (cur/get-dynamic "resize-nesw" rotation))
                     (if ^boolean scale-text
                       (cur/get-dynamic "scale-nwse" rotation)
                       (cur/get-dynamic "resize-nwse" rotation)))
        tpos       (gpt/transform (gpt/point cx cy) transform)
        cx'        (dm/get-prop tpos :x)
        cy'        (dm/get-prop tpos :y)]

    [:g.resize-handler
     [:circle {:r (/ resize-point-radius zoom)
               :style {:fillOpacity "1"
                       :strokeWidth "1px"
                       :vectorEffect "non-scaling-stroke"}
               :fill "var(--color-white)"
               :stroke color
               :cx cx'
               :cy cy'}]

     (if (= align :outside)
       (let [radius   (/ resize-point-circle-radius zoom)
             offset-x (if (or (= position :top-right)
                              (= position :bottom-right))
                        0
                        (- resize-point-circle-radius))
             offset-y (if (or (= position :bottom-left)
                              (= position :bottom-right))
                        0
                        (- resize-point-circle-radius))
             cx       (+ cx offset-x)
             cy       (+ cy offset-y)
             tpos     (gpt/transform (gpt/point cx cy) transform)
             cx'      (dm/get-prop tpos :x)
             cy'      (dm/get-prop tpos :y)]

         [:rect {:x cx'
                 :y cy'
                 :class cursor
                 :width radius
                 :height radius
                 :transform (when rotation (dm/fmt "rotate(%, %, %)" rotation cx' cy'))
                 :style {:fill (if ^boolean (dbg/enabled? :handlers)
                                 "red"
                                 "none")
                         :stroke-width 0}
                 :on-pointer-down #(on-resize tpos %)}])

       [:circle {:on-pointer-down #(on-resize tpos %)
                 :r (/ resize-point-circle-radius zoom)
                 :cx cx'
                 :cy cy'
                 :class cursor
                 :style {:fill (if ^boolean (dbg/enabled? :handlers)
                                 "red"
                                 "none")
                         :stroke-width 0}}])]))

;; The side handler is always rendered horizontally and then rotated"
(mf/defc resize-side-handler
  {::mf/wrap-props false}
  [{:keys [x y length align angle zoom position rotation transform on-resize color show-handler?]}]
  (let [res-point     (if (or (= position :top)
                              (= position :bottom))
                        {:y y}
                        {:x x})
        layout        (mf/deref refs/workspace-layout)
        scale-text    (:scale-text layout)
        height        (/ resize-side-height zoom)
        offset-y      (if (= align :outside) (- height) (- (/ height 2)))
        target-y      (+ y offset-y)
        transform-str (dm/str (gmt/multiply transform (gmt/rotate-matrix angle (gpt/point x y))))

        rect-class    (if (or (= position :left)
                              (= position :right))
                        (if ^boolean scale-text
                          (cur/get-dynamic "scale-ew" rotation)
                          (cur/get-dynamic "resize-ew" rotation))
                        (if ^boolean scale-text
                          (cur/get-dynamic "scale-ns" rotation)
                          (cur/get-dynamic "resize-ns" rotation)))]
    [:g.resize-handler
     (when ^boolean show-handler?
       [:circle {:r (/ resize-point-radius zoom)
                 :style {:fillOpacity 1
                         :stroke color
                         :strokeWidth "1px"
                         :fill "var(--color-white)"
                         :vectorEffect "non-scaling-stroke"}
                 :cx (+ x (/ length 2))
                 :cy y
                 :transform transform-str}])
     [:rect {:x x
             :y target-y
             :width length
             :height height
             :class rect-class
             :transform transform-str
             :on-pointer-down #(on-resize res-point %)
             :style {:fill (if (dbg/enabled? :handlers) "yellow" "none")
                     :stroke-width 0}}]]))

(defn- displacement-transform?
  "Check if the current transform is move or rotate"
  [transform-type]
  (or (= transform-type :move)
      (= transform-type :rotate)))

(mf/defc controls-selection
  {::mf/wrap-props false}
  [{:keys [shape zoom color on-move-selected on-context-menu disable-handlers]}]
  (let [transform-type  (mf/deref refs/current-transform)
        transform (gsh/transform-str shape)
        selrect (:selrect shape)]

    (when (and (not ^boolean (:transforming shape))
               (not ^boolean (displacement-transform? transform-type)))
      [:g.controls {:pointer-events (if ^boolean disable-handlers "none" "visible")}
       (when (some? selrect)
         [:& selection-rect {:rect selrect
                             :transform transform
                             :zoom zoom
                             :color color
                             :on-move-selected on-move-selected
                             :on-context-menu on-context-menu}])])))

(defn- adapt-control-handler-rotation
  "Given a current rotation, shape and handler position, adapt the
  rotation with corresponding rotation offset"
  [rotation shape position]
  (let [flip-x (:flip-x shape)
        flip-y (:flip-y shape)]
    (cond
      (and (or (= position :top-left)
               (= position :bottom-right))
           (or (and ^boolean flip-x (not ^boolean flip-y))
               (and ^boolean flip-y (not ^boolean flip-x))))
      (- rotation 90)

      (and (or (= position :top-right)
               (= position :bottom-left))
           (or (and ^boolean flip-x (not ^boolean flip-y))
               (and ^boolean flip-y (not ^boolean flip-x))))
      (+ rotation 90)

      :else
      rotation)))

(mf/defc controls-handlers
  {::mf/wrap-props false}
  [{:keys [shape zoom color on-resize on-rotate disable-handlers]}]
  (let [transform-type (mf/deref refs/current-transform)
        read-only?     (mf/use-ctx ctx/workspace-read-only?)

        selrect        (:selrect shape)
        transform      (gsh/transform-matrix shape)

        rotation       (-> (gpt/point 1 0)
                           (gpt/transform (:transform shape))
                           (gpt/angle)
                           (mod 360))]

    (when (and (not ^boolean read-only?)
               (not ^boolean (displacement-transform? transform-type))
               (not ^boolean (:transforming shape)))
      [:g.controls {:pointer-events (if ^boolean disable-handlers "none" "visible")}

       (for [handler (handlers-for-selection selrect shape zoom)]
         (let [type     (unchecked-get handler "type")
               position (unchecked-get handler "position")
               props    (unchecked-get handler "props")
               rotation (adapt-control-handler-rotation rotation shape position)
               props    (obj/merge!
                         #js {:key (dm/str (name type) "-" (name position))
                              :zoom zoom
                              :position position
                              :on-rotate on-rotate
                              :on-resize (partial on-resize position)
                              :transform transform
                              :rotation rotation
                              :color color}
                         props)]
           (case type
             :rotation (mf/html [:> rotation-handler props])
             :resize-point (mf/html [:> resize-point-handler props])
             :resize-side (mf/html [:> resize-side-handler props]))))])))

;; --- Selection Handlers (Component)

(mf/defc text-edition-selection
  {::mf/wrap-props false}
  [{:keys [shape color zoom]}]
  (let [x (dm/get-prop shape :x)
        y (dm/get-prop shape :y)
        w (dm/get-prop shape :width)
        h (dm/get-prop shape :height)]

    [:g.controls
     [:rect.main {:x x :y y
                  :transform (gsh/transform-str shape)
                  :width w
                  :height h
                  :pointer-events "visible"
                  :style {:stroke color
                          :stroke-width (/ 0.5 zoom)
                          :stroke-opacity 1
                          :fill "none"}}]]))

(mf/defc multiple-handlers
  {::mf/wrap-props false}
  [{:keys [shapes selected zoom color disable-handlers]}]
  (let [shape (mf/with-memo [shapes]
                (-> shapes
                    (gsh/shapes->rect)
                    (assoc :type :multiple)
                    (cts/setup-shape)))
        on-resize
        (mf/use-fn
         (mf/deps selected shape)
         (fn [current-position _initial-position event]
           (when (dom/left-mouse? event)
             (dom/stop-propagation event)
             (st/emit! (dw/start-resize current-position selected shape)))))

        on-rotate
        (mf/use-fn
         (mf/deps shapes)
         (fn [event]
           (when (dom/left-mouse? event)
             (dom/stop-propagation event)
             (st/emit! (dw/start-rotate shapes)))))]

    [:& controls-handlers
     {:shape shape
      :zoom zoom
      :color color
      :disable-handlers disable-handlers
      :on-resize on-resize
      :on-rotate on-rotate}]))

(mf/defc multiple-selection
  {::mf/wrap-props false}
  [{:keys [shapes zoom color disable-handlers on-move-selected on-context-menu]}]
  (let [shape (mf/with-memo [shapes]
                (-> shapes
                    (gsh/shapes->rect)
                    (assoc :type :multiple)
                    (cts/setup-shape)))]

    [:& controls-selection
     {:shape shape
      :zoom zoom
      :color color
      :disable-handlers disable-handlers
      :on-move-selected on-move-selected
      :on-context-menu on-context-menu}]))

(mf/defc single-handlers
  {::mf/wrap-props false}
  [{:keys [shape zoom color disable-handlers] :as props}]
  (let [shape-id (dm/get-prop shape :id)

        on-resize
        (mf/use-fn
         (mf/deps shape)
         (fn [current-position _initial-position event]
           (when (dom/left-mouse? event)
             (dom/stop-propagation event)
             (st/emit! (dw/start-resize current-position #{shape-id} shape)))))

        on-rotate
        (mf/use-fn
         (mf/deps shape)
         (fn [event]
           (when (dom/left-mouse? event)
             (dom/stop-propagation event)
             (st/emit! (dw/start-rotate [shape])))))]

    [:& controls-handlers
     {:shape shape
      :zoom zoom
      :color color
      :disable-handlers disable-handlers
      :on-rotate on-rotate
      :on-resize on-resize}]))

(mf/defc single-selection
  {::mf/wrap-props false}
  [props]
  [:> controls-selection props])

(mf/defc selection-area
  {::mf/wrap [mf/memo]
   ::mf/wrap-props false}
  [{:keys [shapes edition zoom disable-handlers on-move-selected on-context-menu]}]
  (let [num      (count shapes)
        shape    (first shapes)
        shape-id (dm/get-prop shape :id)

        ;; Note that we don't use mf/deref to avoid a repaint dependency here
        objects  (deref refs/workspace-page-objects)

        color  (if (and ^boolean (= num 1)
                        ^boolean (ctn/in-any-component? objects shape))
                 selection-rect-color-component
                 selection-rect-color-normal)]
    (cond
      (zero? num)
      nil

      (> num 1)
      [:& multiple-selection
       {:shapes shapes
        :zoom zoom
        :color color
        :disable-handlers disable-handlers
        :on-move-selected on-move-selected
        :on-context-menu on-context-menu}]

      (= edition (:id shape))
      (if (cfh/text-shape? shape)
        [:& text-edition-selection
         {:shape shape
          :zoom zoom
          :color color}]
        nil)

      :else
      [:& single-selection
       {:shape shape
        :zoom zoom
        :color color
        :disable-handlers disable-handlers
        :on-move-selected on-move-selected
        :on-context-menu on-context-menu}])))

(mf/defc selection-handlers
  {::mf/wrap [mf/memo]
   ::mf/wrap-props false}
  [{:keys [shapes selected edition zoom disable-handlers]}]
  (let [num      (count shapes)
        shape    (first shapes)
        shape-id (dm/get-prop shape :id)

        ;; Note that we don't use mf/deref to avoid a repaint dependency here
        objects (deref refs/workspace-page-objects)

        color (if (and ^boolean (= num 1)
                       ^boolean (ctn/in-any-component? objects shape))
                selection-rect-color-component
                selection-rect-color-normal)]

    (cond
      (zero? num)
      nil

      (> num 1)
      [:& multiple-handlers
       {:shapes shapes
        :selected selected
        :zoom zoom
        :color color
        :disable-handlers disable-handlers}]

      (= edition shape-id)
      (if (cfh/text-shape? shape)
        nil
        [:& path-editor
         {:zoom zoom
          :shape shape}])

      :else
      [:& single-handlers
       {:shape shape
        :zoom zoom
        :color color
        :disable-handlers disable-handlers}])))
