(ns shitsuke.hiccup
  "Minimal, dependency-free hiccup → HTML string renderer (.cljc, babashka-safe).

  This is the SSR twin of the reagent view contract: the SAME hiccup data that
  reagent renders live in the browser is rendered to an HTML string here for the
  static build (the kami-mangaka-reader dual-render pattern). It unifies the two
  near-identical emitters that previously lived in kami.mangaka.hiccup and
  slides.hiccup.

  Supported:
    [:tag attrs? & children]            vector node
    keyword tags with .class/#id sugar  :div.a.b#id
    attribute maps                      :class as string/vec, boolean attrs
    strings/numbers                     strings escaped, numbers str'd
    nil / seqs                          nil skipped, seqs flattened
    [:hiccup/raw \"<svg/>\"]            trusted markup, not escaped"
  (:require [clojure.string :as str]))

(defn esc
  "Escape &, <, >, \" for safe inclusion in HTML text/attribute context."
  [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(def ^:private void-tags
  #{"area" "base" "br" "col" "embed" "hr" "img" "input"
    "link" "meta" "param" "source" "track" "wbr"})

(defn- parse-tag
  "':div.a.b#id' → [\"div\" {:class \"a b\" :id \"id\"}]. Bare keyword → [name {}]."
  [kw]
  (let [s (name kw)
        id      (second (re-find #"#([^.#]+)" s))
        classes (map second (re-seq #"\.([^.#]+)" s))
        tag     (or (second (re-find #"^([^.#]+)" s)) "div")]
    [tag (cond-> {}
            (seq classes) (assoc :class (str/join " " classes))
            id            (assoc :id id))]))

(defn- class-str
  [v]
  (cond (string? v) v
        (coll? v)    (str/join " " (filter identity v))
        :else        (str v)))

(defn- style-map->css
  "Render a reagent-style :style map {:font-size \"10px\" :color \"#fff\"} as a
  CSS declaration string `font-size:10px;color:#fff;`. Lets the same hiccup
  data carry inline styles through both reagent (cljs, map) and ->html (SSR,
  string). Keys are keyword/string names; nil/false values are skipped."
  [m]
  (->> m
       (keep (fn [[k v]]
               (when (and v (not (false? v)))
                 (str (name k) ":" (if (true? v) "true" v) ";"))))
       (str/join "")))

(defn- render-attrs [attrs]
  (->> attrs
       (keep (fn [[k v]]
               (when (and v (not (false? v)))
                 (let [k (name k)]
                   (cond
                     (= k "class")
                     (str " " k "=\"" (esc (class-str v)) "\"")
                     (and (= k "style") (map? v))
                     (str " " k "=\"" (esc (style-map->css v)) "\"")
                     (true? v)
                     (str " " k)
                     :else
                     (str " " k "=\"" (esc v) "\""))))))
       (apply str)))

(declare ->html)

(defn- render-node [node sb]
  (cond
    (nil? node) sb
    (string? node) (conj! sb (esc node))
    (number? node) (conj! sb (str node))
    (and (vector? node) (= :hiccup/raw (first node)))
    (conj! sb (str (second node)))
    ;; vector of nodes: [[:p "a"] [:p "b"]] — first child is itself a vector.
    (and (vector? node) (not (empty? node)) (vector? (first node)))
    (reduce (fn [s c] (render-node c s)) sb node)
    (vector? node)
    (let [[t & body] node
          [tag base] (parse-tag t)
          [attrs children] (if (map? (first body))
                             [(first body) (rest body)]
                             [{} body])
          ;; tag-sugar classes/id + attr :class merge (space-joined, both win)
          attrs (merge-with (fn [a b] (str a " " b)) base attrs)
          ;; <textarea> special case: real HTML has no value attribute on
          ;; textarea — the pre-filled text is the element *content*. The live
          ;; (reagent/React) side of the dual-render contract needs :value as
          ;; an attribute (value-as-child is read only at mount), so the SSR
          ;; twin translates: render :value as escaped content, emit no value=.
          textarea-value (when (= tag "textarea") (:value attrs))
          attrs (cond-> attrs (= tag "textarea") (dissoc :value))]
      (conj! sb (str "<" tag (render-attrs attrs) ">"))
      (when-not (contains? void-tags tag)
        (when (some? textarea-value)
          (conj! sb (esc textarea-value)))
        (reduce (fn [s c] (render-node c s)) sb children)
        (conj! sb (str "</" tag ">")))
      sb)
    (seq? node) (reduce (fn [s c] (render-node c s)) sb node)
    :else (conj! sb (esc node))))

(defn ->html
  "Render a hiccup node (or seq of nodes) to an HTML string."
  [node]
  (str/join (persistent! (render-node node (transient [])))))
