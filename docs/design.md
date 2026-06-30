# shitsuke — design

This is the layer-by-layer API reference for `shitsuke`. For the *why* see
`docs/adr/0001-shitsuke-design-system.md`; for the superproject-level decision
see `90-docs/adr/<ts>-shitsuke-design-system.md`.

## Layer 1 — `shitsuke.tokens`

Token IR (web-CSS-oriented, forward-compatible with `office-style`'s `:style-ir`
and `slides.design`'s `:slides/theme`):

```clojure
{:shitsuke/colors     {<token> <css-color>}            ; "#496B9A" (with #)
 :shitsuke/type       {<token> {:font-family ... :font-size ... :font-weight ... :color ...}}
 :shitsuke/spacing    {<token> <css-length>}           ; "16px"
 :shitsuke/motion     {<token> {:duration ... :easing ...}}
 :shitsuke/breakpoints {<token> <px>}}
```

API:
- `default-tokens` — the v1 token set.
- `(deep-merge & maps)` — right-biased recursive merge (ported from `slides.design`).
- `(resolve-tokens overrides)` — `default-tokens` deep-merged with a partial override map.
- `(css-variables overrides?)` — emit `:root { --shitsuke-<group>-<name>: ...; }` CSS string.
  Nested maps (e.g. `:shitsuke/type`) expand to per-prop vars
  (`--shitsuke-type-title-font-size`).
- `(normalize-hex s)` — slides.design stores colors without `#`; this prepends `#` if absent.
- `(from-slides-design deck-design)` — adapter: build a shitsuke token-override map from a
  `slides.design` deck design (`:slides/theme` colors/fonts + `:slides/text-styles`).

## Layer 2 — `shitsuke.hiccup`

Dependency-free hiccup → HTML string renderer (`.cljc`, babashka-safe). The SSR
twin of the reagent view contract.

- `(esc s)` — escape `& < > "`.
- `(->html node)` — render a hiccup node (or seq of nodes) to an HTML string.

Supported: `[:tag attrs? & children]`, `:div.a.b#id` tag sugar, `:class` as
string/vec, boolean attrs (`true` → bare, `false` → dropped), strings (escaped),
numbers, nil (skipped), seqs (flattened), `[:hiccup/raw "<svg/>"]` (trusted, not
escaped). Void tags (`img`/`input`/`br`/…) emit no closing tag.

## Layer 3 — `shitsuke.style`

- `(class-name component)` → `"shitsuke__<component>"` (stable scoped class; the
  shadow-css extraction anchor and the hiccup `:class`).
- `(root-css overrides?)` → `:root{...}` CSS string from tokens.
- `(inline-style css?)` → `<style>…</style>` string for SSR embedding.
- `(inline-style-hiccup css?)` → `[:style [:hiccup/raw css]]` (raw so CSS survives
  `->html` unescaped).

## Layer 4 — `shitsuke.re-frame` / `.core`

`shitsuke.re-frame` — the mini runtime (7 functions, mirrors `wasm-ui`'s
`kotoba.wasm.re-frame`): `app-db`, `clear!`, `reg-event-db`, `reg-sub`,
`dispatch`, `dispatch-sync`, `subscribe`. `subscribe` returns an `IDeref` whose
`deref` recomputes synchronously against `app-db` (no reaction graph).

`shitsuke.re-frame.core` — the host seam. App code requires
`[shitsuke.re-frame.core :as rf]` (NOT `re-frame.core` directly):
- `:cljs` → real `re-frame 1.4.3` (`reg-event-db`/`reg-sub` via referred macros;
  `dispatch`/`dispatch-sync`/`subscribe`/`clear!`/`app-db` via re-exported vars).
- `:clj` → the mini runtime.

**Portable subset (app code MUST stay within):** `reg-event-db`, `reg-sub`,
`dispatch`, `dispatch-sync`, `subscribe`, `clear!`, `app-db`. MUST NOT use:
`reg-event-fx`, `reg-fx`, `reg-cofx`, `inject-cofx`, interceptors, subscription
chaining (`<-`). Pinned by `test/shitsuke/re_frame_test.cljc`.

## Layer 5 — `shitsuke.reagent.core`

Host seam for the view layer. Views are pure hiccup data (no reagent import).
- `:cljs` → real `reagent 1.2.0` (`as-element`, `rdom/render`).
- `:clj` → `shitsuke.hiccup/->html` (`as-element` is identity; `render` returns the string).

`(render hic)` mounts (cljs) / serialises (clj) the hiccup.

## Layer 6 — `shitsuke.components`

Pure-hiccup primitives returning `[:tag {:class "shitsuke__<component>" …} …]`:

| fn | shape |
|---|---|
| `button` / `icon-button` | `(label opts?)` — `:act`, `:disabled`, `:title`, `:type` |
| `field` | `(label-text control opts?)` — label + control row |
| `input` | `(opts)` — `:id`, `:value`, `:placeholder`, `:type`, `:on-input`, `:act` |
| `textarea` | `(opts)` — `:id`, `:value`, `:rows`, `:on-input`, `:act` |
| `select` | `(options opts)` — `options` = `[ [value label] … ]`, `:value`, `:on-change`, `:act` |
| `card` | `(body opts?)` — `:class`, `:id` |
| `toolbar` | `(actions opts?)` — horizontal action row |
| `mode-tabs` | `(tabs current opts?)` — `tabs` = `[ [id label] … ]` |
| `thumb` | `(body active? opts?)` — `:act` |
| `pane` | `(hidden? body)` — visibility-toggled pane |

`act` is the portable interaction attribute (a keyword). On cljs the caller
wraps it into `:on-click #(rf/dispatch [act …])`; on SSR the caller emits
`data-act="<act>"` and a thin enhancer dispatches on click (mangaka
`wire-lang-switch!` pattern).

## Styling contract (shadow-css :pages build)

A consumer's `:pages` build (e.g. `slides.build`) adds `shitsuke.components` to
`shadow.css.build`'s `:include` so the `(css …)` markers materialise scoped
rules for `shitsuke__*` classes. `:root` vars come from
`shitsuke.style/root-css` (Tier A, portable). Components never carry inline
visual CSS — only stable class names + `var(--shitsuke-…)` references.
