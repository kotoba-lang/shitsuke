# shitsuke

`shitsuke`（仕付け）is the Kotoba shared **UI design system**: design tokens,
hiccup renderer, style (CSS) layer, portable re-frame seam, pure-hiccup
component primitives, and small host-independent editor kernels. One set of `.cljc` view code renders two ways — SSR
(`shitsuke.hiccup/->html`, clj/babashka) and live browser (reagent + re-frame,
cljs) — the dual-render contract proven by `kami-mangaka-reader-clj`.

The implementation is portable `.cljc` (JVM / ClojureScript / SCI / babashka).
It owns no filesystem, network, or host effects. Real `reagent` / `re-frame` /
`shadow-css` are build/dev aliases only; the core has zero third-party runtime
deps (same split as `dot` / `kasane` / `slides`).

```text
shitsuke = tokens + hiccup + style + re-frame seam + components + editor kernels
```

## Boundaries

| layer | role |
|---|---|
| `shitsuke.tokens` | design-token IR + resolver + `:root` CSS-var emitter; `from-slides-design` adapter |
| `shitsuke.hig` | Apple-HIG semantic token layer: 11 text styles + fluid display scale (`:display1/2/3`), semantic colors (light+dark), palette, spacing/radius, element base CSS — `--hig-*` vars inside cascade layer `kotoba.hig` |
| `shitsuke.hiccup` | dependency-free hiccup → HTML string renderer (SSR twin of the view contract) |
| `shitsuke.style` | token → CSS custom properties + stable `shitsuke__*` class-name registry |
| `shitsuke.re-frame` | tiny re-frame-shaped runtime (7-fn portable subset) for JVM/SSR/WASM |
| `shitsuke.re-frame.core` | host seam: real re-frame (cljs) ‖ mini runtime (clj) |
| `shitsuke.reagent.core` | host seam: real reagent (cljs) ‖ `hiccup/->html` (clj) |
| `shitsuke.components` | pure-hiccup UI primitives (button/field/input/toolbar/mode-tabs/…) |
| `kotoba.editor` | portable editor state helpers: selection, undo/redo, nudge, alignment |
| host build | shadow-css `:pages` extraction, reagent/re-frame `:cljs` aliases |

## Dual render (the contract)

```clojure
(require '[shitsuke.components :as c]
         '[shitsuke.hiccup :as h])

(def view [:div [:c/button "Go" {:act :go}]]) ; pure hiccup data

;; SSR (clj / babashka):
(h/->html view)

;; Browser (cljs): the SAME `view` is returned by a reagent component and
;; mounted via shitsuke.reagent.core/render; state via shitsuke.re-frame.core.
```

## `shitsuke.hig` — Apple-HIG base layer

`shitsuke.hig` is the single source of truth for Apple Human Interface
Guidelines-grade typography (the 11 UIKit text styles), font stacks
(`--hig-font-text` / `--hig-font-display` / `--hig-font-mono`), semantic
colors (light + dark), system palette, 4pt-grid spacing, radius, and
element-level base CSS. It emits `--hig-*` CSS custom properties and element
rules inside the CSS cascade layer `kotoba.hig`
(`(hig-css overrides dark-overrides)` = the full bundle; the order
declaration is `@layer kotoba.hig, kotoba.glass;`). Monospace UI (code
panes, EDN editors) uses `var(--hig-font-mono)` or the `.hig-mono` utility
class (mono stack + footnote size) — no hand-written mono stacks in apps.

Hero/marketing type uses the opt-in **display scale** above `:large-title`:
`:display3` / `:display2` / `:display1` (w700, display stack, tight
tracking) with **fluid** `clamp()` font sizes — e.g. `:display3` is
`clamp(40px, 5vw + 8px, 64px)` (min = 62.5% of max, ramping 640→1120px
viewport; line-height `calc(1em + 4px)` = 64/68 at max). Apply via the
`.hig-display1/2/3` utility classes or the `--hig-text-display*-*` vars —
no hand-written viewport clamps in apps. `base-css` is unchanged (`h1`
stays `:large-title`). See `docs/design.md` for the formula table.

- **liquid-glass-ui** fills the `kotoba.glass` layer with its material
  (translucency/vibrancy) styles on top of this base.
- **Apps** consume both via **kotoba-ui**; app CSS stays *unlayered*, so it
  always beats both layers.
- Additive to `shitsuke.tokens` — v1 `--shitsuke-*` vars stay for existing
  consumers.

## `.kotoba` form-A port (ADR-2607270100 §10)

`kotoba/tokens_core.kotoba` and `kotoba/hig_core.kotoba` port the pure
token → CSS-string pipeline (CSS custom-property emission, layer-order,
text-style-props). Dual-render seams (`reagent` / `re-frame`) stay on the
`.cljc` host side. Consumer APIs (`shitsuke.tokens` / `shitsuke.hig`) are
unchanged — this is an oracle-backed experiment ahead of W4 recursive
values, not the final API. Byte-equality is gated by
`test/shitsuke/kotoba_parity_test.clj` (compiler is test-only).

### Which cores actually RUN (ADR-2608120200 §1)

**One does: `kotoba/hiccup_core.kotoba`.** `shitsuke.hiccup` executes the
shipped `resources/shitsuke/oracle/hiccup-core.kir.edn` through
`shitsuke.kotoba-oracle` for every RAWTEXT breakout judgement, and this
namespace no longer contains a `re-find` for it. That one was taken first
because it is a SECURITY predicate — whether a `<script>`/`<style>` payload
contains the `</tag` sequence that ends the element early and lets markup be
injected after it — and a security rule that exists twice can be fixed once
and stay broken in the copy that runs.

**The other five do not, and that is a measured decision, not a backlog.**

| core | status | measured reason |
|---|---|---|
| `hiccup_core` | **runs** | record of two `:string`s; delegates whole |
| `tokens_core`, `hig_core` | parity gate | see below — structurally delegable, but the cost lands on browsers |
| `style_core`, `tokens_document`, `hig_document` | parity gate | not attempted in this tranche |

The token/HIG emitters split in two:

- `group-css` / `nested-css` **cannot cross at all.** They carry a
  `[:map :keyword :string]` inside the guest, and the 64-node ADT limit refuses
  at **30 entries** (measured 2026-08-12; max accepted is 29, for both). The
  largest real group today is **18** (`:hig/color`, `:hig/palette`) — 1.6×
  headroom on a collection that grows every time the design system gains a
  token, against the 10× that made `fsm` delegable in ADR-2608112100.
- Every other export **is** structurally delegable — all-`:string` record
  fields, no `:i64` field, and no `string-substring` in the compiled KIR — but
  delegating them was **declined**. Their host call sites are
  `shitsuke.tokens/css-variables` and `shitsuke.hig/hig-css`, and
  `kotoba-ui.theme/theme-css` calls the latter on the browser render path of a
  library 24 repos depend on. Delegation would move emitting the design
  system's CSS from *works on require* to *throws unless `register-kir!` ran
  first*. `rawtext-breakout?` does not pay that: on ClojureScript apps render
  through reagent, not through `->html`. Two of them (`hig/layer-order-css`,
  `hig/text-style-classes`) are additionally top-level `def`s, so delegating
  them would make *loading* the design system depend on a registered artifact.

### ClojureScript consumers: what changed

**Loading is unchanged.** Nothing delegated is evaluated at load time, so
`(require '[shitsuke.hiccup])` works exactly as before.

**One call changed.** Rendering a `<script>` or `<style>` element through
`shitsuke.hiccup/->html` on ClojureScript now needs the KIR registered first,
because there is no classpath to read the artifact from:

```clojure
(require '[shitsuke.kotoba-oracle :as oracle] '[cljs.reader :as reader])
(oracle/register-kir! :hiccup-core (reader/read-string <hiccup-core.kir.edn>))
```

Without it, `->html` **throws** rather than skipping the check. That is
deliberate: a silent fallback around a missing security core is how an
unchecked payload reaches a page. Everything else in `shitsuke.hiccup` —
including `->html` of any markup with no raw-text element — is untouched.

`clojure -M:cljs-check` runs the delegated path on ClojureScript/Node and
asserts both the refusal and the answers. A green JVM suite is not evidence
about ClojureScript: two runtime asymmetries in the KIR interpreter were
measured across the fleet on 2026-08-12 that are invisible from the JVM.

## Tests

```bash
clojure -M:test        # JVM suite, including the drift and delegation gates
clojure -M:cljs-check  # the delegated path on ClojureScript/Node
clojure -M:test:gen    # regenerate resources/shitsuke/oracle/*.kir.edn
```

## Design

See `docs/design.md` for the layer-by-layer API and `docs/adr/0001-shitsuke-design-system.md`
for the decision record.
