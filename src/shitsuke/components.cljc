(ns shitsuke.components
  "Pure-hiccup UI primitives (.cljc, no reagent import).

  Each fn returns plain hiccup data (the kami-mangaka dual-render contract): the
  SAME data is rendered live by reagent in the browser and to HTML by
  shitsuke.hiccup/->html for SSR. Styling is via stable `shitsuke__<component>`
  classes (shitsuke.style/class-name) + token CSS vars (var(--shitsuke-...)),
  so the visual lives in the shadow-css :pages build, not inline.

  `act` is the portable interaction attribute: a keyword like :slides/new-deck.
  On :cljs the caller wraps it in :on-click (rf/dispatch [act ...]); on SSR the
  caller emits `data-act=\"<act>\"` and a thin enhancer dispatches on click
  (mirrors mangaka reader's wire-lang-switch! pattern)."
  (:require [shitsuke.style :as s]))

(defn- act->str
  "Render an `act` value to the data-act attribute string, preserving the
  keyword namespace: :cart/add → \"cart/add\", :new-deck → \"new-deck\".
  Strings pass through; nil stays nil (attribute dropped)."
  [a]
  (cond
    (nil? a) nil
    (string? a) a
    (keyword? a) (if-let [ns (namespace a)]
                   (str ns "/" (name a))
                   (name a))
    :else (str a)))

(defn button
  "Plain button. `label` may be string or hiccup. opts: :class, :act, :disabled,
  :title, :type."
  ([label]
   (button label nil))
  ([label opts]
   (let [{:keys [act disabled title type class]} opts]
     [:button {:class (str (s/class-name :button) (when class (str " " class)))
               :type (or type "button")
               :disabled (when disabled true)
               :title title
               :data-act (some-> act act->str)}
      label])))

(defn icon-button
  "Button whose label is an icon glyph (string/hiccup). Same opts as button."
  ([icon]
   (icon-button icon nil))
  ([icon opts]
   (button icon (assoc opts :class (str (s/class-name :icon-button)
                                        (when-let [c (:class opts)] (str " " c)))))))

(defn field
  "Label + control row. `control` is hiccup (input/textarea/select)."
  ([label-text control]
   (field label-text control nil))
  ([label-text control opts]
   (let [{:keys [for-id class]} opts]
     [:div {:class (str (s/class-name :field) (when class (str " " class)))}
      (when label-text [:label {:for for-id} label-text])
      control])))

(defn input
  "Text input. opts: :id, :value, :placeholder, :type, :on-input (cljs), :act (ssr)."
  ([opts]
   (let [{:keys [id value placeholder type on-input act]} opts]
     [:input {:id id
              :class (s/class-name :input)
              :type (or type "text")
              :value (or value "")
              :placeholder placeholder
              :on-input on-input
              :data-act (some-> act act->str)}])))

(defn textarea
  ([opts]
   (let [{:keys [id value rows placeholder on-input act]} opts]
     [:textarea {:id id
                 :class (s/class-name :textarea)
                 :rows (or rows 6)
                 :placeholder placeholder
                 :on-input on-input
                 :data-act (some-> act act->str)}
      (or value "")])))

(defn select
  "`options` is a vec of [value label] pairs. opts: :id, :value, :on-change, :act."
  ([options opts]
   (let [{:keys [id value on-change act]} opts]
     [:select {:id id
               :class (s/class-name :select)
               :on-change on-change
               :data-act (some-> act act->str)}
      (for [[v l] options]
        [:option {:value v :selected (= (str v) (str value))} l])])))

(defn card
  "Box container. `body` is hiccup or seq. opts: :class, :id."
  ([body]
   (card body nil))
  ([body opts]
   (let [{:keys [class id]} opts]
     [:section {:id id
                :class (str (s/class-name :card) (when class (str " " class)))}
      body])))

(defn toolbar
  "Horizontal action row. `actions` is a seq of hiccup (typically buttons)."
  ([actions]
   (toolbar actions nil))
  ([actions opts]
   [:header {:class (str (s/class-name :toolbar) (when-let [c (:class opts)] (str " " c)))}
    (into [:nav] actions)]))

(defn mode-tabs
  "Tab strip. `tabs` is [id label] pairs; `current` is the active id."
  ([tabs current]
   (mode-tabs tabs current nil))
  ([tabs current opts]
   [:nav {:class (s/class-name :mode-tabs)}
    (for [[id label] tabs]
      [:button {:key (name id)
                :type "button"
                :class (str (s/class-name :tab)
                            (when (= id current) (str " " (s/class-name :tab--active))))
                :data-act (some-> id act->str)}
       label])]))

(defn thumb
  "Selectable thumbnail. `active?` toggles the active class. `body` is hiccup."
  ([body active?]
   (thumb body active? nil))
  ([body active? opts]
   [:button {:type "button"
             :class (str (s/class-name :thumb) (when active? (str " " (s/class-name :thumb--active))))
             :data-act (some-> (:act opts) act->str)}
    body]))

(defn pane
  "Visibility-toggled pane. `hidden?` hides it. `body` is hiccup."
  ([hidden? body]
   [:section {:class (s/class-name :pane)
              :hidden (when hidden? true)}
    body]))
