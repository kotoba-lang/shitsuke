(ns shitsuke.kotoba-parity-test
  "Byte-equality gate between shitsuke.tokens / shitsuke.hig and their
  `.kotoba` form-A ports (kotoba/tokens_core.kotoba, kotoba/hig_core.kotoba),
  the third step of the design-system migration in ADR-2607270100 section 10
  (css → html → shitsuke → liquid-glass-ui → kotoba-ui).

  Dual-render seams (reagent/re-frame) stay on the .cljc host side — only the
  pure token → CSS-string pipeline is gated here.

  The port is compiled and executed through the KIR interpreter in this same
  JVM. Each case is a zero-argument `.kotoba` function; no typed value is
  marshalled from Clojure. Map walks are key-sorted on both sides."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.compiler.core :as compiler]
            [kotoba.kir :as ir]
            [shitsuke.hig :as hig]
            [shitsuke.tokens :as tokens]))

(def tokens-source (slurp "kotoba/tokens_core.kotoba"))
(def hig-source (slurp "kotoba/hig_core.kotoba"))

(defn- kotoba-literal [s]
  (str \" (-> s (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) \"))

(defn- compile-cases
  [port-source cases]
  (let [defs (for [[name body] cases]
               (str "(defn " name " [] :string " body ")"))
        kir (:kir (compiler/compile-source
                   (str port-source "\n" (str/join "\n" defs)) :wasm32-kotoba-v1 {}))]
    (into {} (map (fn [[name _]] [name (ir/execute kir (symbol name) [])]) cases))))

(defn- typed-map-literal [m]
  (str "(typed-map-new [:map :keyword :string] "
       (str/join " " (mapcat (fn [[k v]] [(pr-str k) (kotoba-literal (str v))])
                             (sort-by (comp str key) m)))
       ")"))

(defn- cljc-scalar-group-css
  "Key-sorted reconstruction of shitsuke.tokens/pair->css for scalar groups."
  [prefix group m]
  (->> (sort-by (comp str key) m)
       (map (fn [[k v]]
              (str "  --" prefix "-" (name group) "-" (name k) ": " v ";")))
       (str/join "\n")))

(defn- cljc-nested-css
  "Key-sorted reconstruction of nested pair->css (type / text styles)."
  [prefix group k props]
  (->> (sort-by (comp str key) props)
       (map (fn [[pk pv]]
              (str "  --" prefix "-" (name group) "-" (name k) "-" (name pk) ": " pv ";")))
       (str/join "\n")))

;; --- tokens ---------------------------------------------------------------

(deftest tokens-normalize-hex-matches
  (let [actual (compile-cases tokens-source
                              {"n1" "(normalize-hex \"496B9A\")"
                               "n2" "(normalize-hex \"#496B9A\")"
                               "n3" "(normalize-hex \"FFFFFF\")"})]
    (is (= (tokens/normalize-hex "496B9A") (get actual "n1")))
    (is (= (tokens/normalize-hex "#496B9A") (get actual "n2")))
    (is (= (tokens/normalize-hex "FFFFFF") (get actual "n3")))))

(deftest tokens-scalar-group-is-byte-identical
  (let [colors (into (sorted-map) (get tokens/default-tokens :shitsuke/colors))
        spacing (into (sorted-map) (get tokens/default-tokens :shitsuke/spacing))
        breakpoints (into (sorted-map) (get tokens/default-tokens :shitsuke/breakpoints))
        actual (compile-cases tokens-source
                              {"colors" (str "(group-css \"colors\" " (typed-map-literal colors) ")")
                               "spacing" (str "(group-css \"spacing\" " (typed-map-literal spacing) ")")
                               "bp" (str "(group-css \"breakpoints\" " (typed-map-literal breakpoints) ")")
                               "root" (str "(root-css (group-css \"colors\" " (typed-map-literal colors) "))")
                               "c_var" "(css-var-name \"colors\" \"ink\")"
                               "c_decl" "(scalar-decl \"colors\" \"ink\" \"#17202A\")"})]
    (testing "css-var-name / scalar-decl"
      (is (= "--shitsuke-colors-ink" (get actual "c_var")))
      (is (= "  --shitsuke-colors-ink: #17202A;" (get actual "c_decl"))))
    (testing "full scalar groups"
      (is (= (cljc-scalar-group-css "shitsuke" "colors" colors) (get actual "colors")))
      (is (= (cljc-scalar-group-css "shitsuke" "spacing" spacing) (get actual "spacing")))
      (is (= (cljc-scalar-group-css "shitsuke" "breakpoints" breakpoints) (get actual "bp"))))
    (testing "root wrapper"
      (is (= (str ":root {\n" (cljc-scalar-group-css "shitsuke" "colors" colors) "\n}")
             (get actual "root"))))
    (testing "default-tokens corpus embeds match key-sorted defaults"
      (is (str/includes? (tokens/css-variables) "--shitsuke-colors-ink: #17202A;"))
      (let [from-port (compile-cases tokens-source {"d" "(default-scalar-root)"})]
        (is (str/includes? (get from-port "d") "--shitsuke-colors-ink: #17202A;"))
        (is (str/includes? (get from-port "d") "--shitsuke-spacing-4: 16px;"))
        (is (str/includes? (get from-port "d") "--shitsuke-breakpoints-md: 940px;"))))))

(deftest tokens-nested-type-decl-matches
  (let [title {:font-family "Aptos Display, system-ui, sans-serif"
               :font-size "38px"
               :font-weight "700"
               :color "var(--shitsuke-colors-ink)"}
        ;; nested-decl is one prop at a time; group of nested lines via
        ;; repeated nested-decl composition is the form-A shape.
        actual (compile-cases tokens-source
                              {"t_fs" "(nested-decl \"type\" \"title\" \"font-size\" \"38px\")"
                               "t_ff" "(nested-decl \"type\" \"title\" \"font-family\" \"Aptos Display, system-ui, sans-serif\")"
                               "t_block"
                               (str "(string-concat (nested-decl \"type\" \"title\" \"color\" "
                                    (kotoba-literal "var(--shitsuke-colors-ink)") ") "
                                    "(string-concat \"\\n\" "
                                    "(string-concat (nested-decl \"type\" \"title\" \"font-family\" "
                                    (kotoba-literal (:font-family title)) ") "
                                    "(string-concat \"\\n\" "
                                    "(string-concat (nested-decl \"type\" \"title\" \"font-size\" \"38px\") "
                                    "(string-concat \"\\n\" "
                                    "(nested-decl \"type\" \"title\" \"font-weight\" \"700\")))))))")})]
    (is (= "  --shitsuke-type-title-font-size: 38px;" (get actual "t_fs")))
    (is (= "  --shitsuke-type-title-font-family: Aptos Display, system-ui, sans-serif;"
           (get actual "t_ff")))
    (is (= (cljc-nested-css "shitsuke" "type" "title" title)
           (get actual "t_block")))
    (is (str/includes? (tokens/css-variables) "--shitsuke-type-title-font-size: 38px;"))))

;; --- hig ------------------------------------------------------------------

(deftest hig-layer-order-and-text-style-props-match
  (let [actual (compile-cases hig-source
                              {"layer" "(layer-order-css)"
                               "body_props" "(text-style-props-token \"body\")"
                               "h1_props" "(text-style-props \"large-title\" \"700\")"
                               "class_body" "(text-style-class \"body\")"
                               "class_d3" "(text-style-class \"display3\")"
                               "wrap" "(layer-wrap (text-style-class \"body\"))"})]
    (is (= hig/layer-order-css (get actual "layer")))
    (testing "text-style-props matches .cljc private emission shape"
      ;; Use the public base-css / text-style-classes as the oracle for shape.
      (is (str/includes? (hig/base-css) (str/trim-newline (get actual "body_props"))))
      (is (str/includes? (hig/base-css)
                         "font-family: var(--hig-text-large-title-font-family);"))
      (is (str/includes? (get actual "h1_props")
                         "font-weight: 700;"))
      (is (str/includes? (get actual "h1_props")
                         "font-family: var(--hig-text-large-title-font-family);")))
    (testing "utility class skeleton"
      (is (str/includes? hig/text-style-classes ".hig-body {"))
      (is (str/includes? hig/text-style-classes ".hig-display3 {"))
      (is (str/includes? (get actual "class_body") ".hig-body {"))
      (is (str/includes? (get actual "class_d3")
                         "font-size: var(--hig-text-display3-font-size);"))
      (is (str/starts-with? (get actual "wrap") "@layer kotoba.hig {")))))

(deftest hig-scalar-and-nested-groups-match
  (let [colors {:label "#000000" :system-background "#FFFFFF" :tint "#007AFF"}
        spacing {:1 "4px" :4 "16px" :content-margin "16px"}
        radius {:capsule "999px" :xs "6px"}
        body-text {:font-family "var(--hig-font-text)"
                   :font-size "17px"
                   :font-weight "400"
                   :line-height "22px"}
        d3 {:font-family "var(--hig-font-display)"
            :font-size "clamp(40px, 5vw + 8px, 64px)"
            :font-weight "700"
            :letter-spacing "-0.02em"
            :line-height "calc(1em + 4px)"}
        actual (compile-cases hig-source
                              {"colors" (str "(group-css \"color\" " (typed-map-literal colors) ")")
                               "spacing" (str "(group-css \"spacing\" " (typed-map-literal spacing) ")")
                               "radius" (str "(group-css \"radius\" " (typed-map-literal radius) ")")
                               "hairline" "(group-scalar-decl \"hairline\" \"0.5px\")"
                               "body" (str "(nested-css \"text\" \"body\" " (typed-map-literal body-text) ")")
                               "d3" (str "(nested-css \"text\" \"display3\" " (typed-map-literal d3) ")")
                               "sample" "(sample-root)"})]
    (is (= (cljc-scalar-group-css "hig" "color" colors) (get actual "colors")))
    (is (= (cljc-scalar-group-css "hig" "spacing" spacing) (get actual "spacing")))
    (is (= (cljc-scalar-group-css "hig" "radius" radius) (get actual "radius")))
    (is (= "  --hig-hairline: 0.5px;" (get actual "hairline")))
    (is (= (cljc-nested-css "hig" "text" "body" body-text) (get actual "body")))
    (is (= (cljc-nested-css "hig" "text" "display3" d3) (get actual "d3")))
    (testing "sample root embeds live in the .cljc default emission"
      (let [css (hig/css-variables)
            sample (get actual "sample")]
        (is (str/includes? css "--hig-color-label: #000000;"))
        (is (str/includes? sample "--hig-color-label: #000000;"))
        (is (str/includes? sample "--hig-text-display3-font-size: clamp(40px, 5vw + 8px, 64px);"))
        (is (str/includes? sample "--hig-hairline: 0.5px;"))
        (is (str/includes? css "--hig-text-display3-font-size: clamp(40px, 5vw + 8px, 64px);"))))))
