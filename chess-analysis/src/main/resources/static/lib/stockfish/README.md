# Stockfish (engine no navegador)

Esta pasta é **opcional**. O `local-engine.js` carrega o Stockfish na
seguinte ordem:

1. **Esta pasta** (`/lib/stockfish/stockfish-18-lite-single.js`)
2. **Proxy do backend** (`/cdn/stockfish/stockfish-18-lite-single.js`) —
   o `StockfishAssetController` baixa do GitHub Release v18.0.0 na
   primeira requisição e cacheia em disco
   (`${java.io.tmpdir}/chess-analyzer-stockfish/`).

Como ambas as URLs são **same-origin** (servidas pelo próprio Spring
Boot), o navegador não bate em CORS do GitHub nem em MIME `text/plain`
do jsDelivr.

## Quando self-hospedar nesta pasta?

- Quando o servidor **não tem acesso à internet** (intranet corporativa,
  air-gapped) — sem isso o proxy não consegue baixar do GitHub.
- Quando você quer **carregamento mais rápido** na primeira execução
  (pulando o download do GitHub).
- Quando quer **versionar** o engine junto com o código.

## Como self-hospedar

Baixe os 2 arquivos abaixo da release oficial v18.0.0 e coloque-os
nesta pasta:

- `stockfish-18-lite-single.js`   (~1 MB, loader + glue code)
- `stockfish-18-lite-single.wasm` (~6 MB, binário WASM NNUE)

Link da release:
https://github.com/nmrugg/stockfish.js/releases/tag/v18.0.0

> Stockfish.js é GPLv3 — by Nathan Rugg, baseado no
> [Stockfish oficial](https://github.com/official-stockfish/Stockfish).

## Engine mais forte (opcional)

Se quiser substituir pela versão **full** (~100 MB total, mais forte),
use `stockfish-18-single.js` + `.wasm` e altere as constantes
`LOCAL_URL` / `PROXY_URL` no início de `static/js/local-engine.js`.
O `StockfishAssetController` já tem todos os builds da v18 na whitelist.
