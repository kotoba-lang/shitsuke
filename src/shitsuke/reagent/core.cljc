(ns shitsuke.reagent.core
  "Host-independent reagent seam (mirrors shitsuke.re-frame.core).

  Views are written as plain hiccup data in .cljc (no reagent import), so they
  serve double duty: reagent renders them live in the browser (cljs) and
  shitsuke.hiccup/->html renders the identical data for SSR (clj). This ns is
  the thin mount/escape hatch that picks the host impl.

    :cljs → real reagent 1.2.0 (rdom/render, as-element)
    :clj  → shitsuke.hiccup/->html (SSR string; render writes the page)"
  #?(:cljs (:require [reagent.core :as r]
                     [reagent.dom :as rdom])
     :clj  (:require [shitsuke.hiccup :as hic])))

#?(:clj
   (do
     (defn as-element
       "SSR: hiccup data is already the element form; identity."
       [hic]
       hic)
     (defn render
       "SSR: render hiccup to an HTML string (caller writes it to the response)."
       [hic]
       (hic/->html hic)))
   :cljs
   (do
     (def as-element r/as-element)
     (defn render
       "Browser: render hiccup into the element identified by id-or-node."
       ([hic] (rdom/render hic))
       ([hic node] (rdom/render hic node)))))
