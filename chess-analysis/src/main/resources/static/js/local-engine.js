/* ============================================================
 *  local-engine.js
 *  ------------------------------------------------------------
 *  Wrapper de alto nível que embarca o Stockfish dentro do
 *  navegador do usuário (mesma arquitetura usada pelo lichess.org)
 *  através de um Web Worker carregando o build WASM single-thread.
 *
 *  - Engine padrão: Stockfish 18 LITE single-thread (~7 MB)
 *    https://github.com/nmrugg/stockfish.js (GPLv3, by Nathan Rugg)
 *
 *  - Estratégia de carregamento (primeira URL acessível ganha):
 *      1) /lib/stockfish/stockfish-18-lite-single.js   (self-host estático)
 *      2) /cdn/stockfish/stockfish-18-lite-single.js   (proxy do backend Spring,
 *                                                       baixa do GitHub e
 *                                                       cacheia em disco)
 *
 *    Ambas são SAME-ORIGIN (servidas pelo próprio Spring Boot) — assim
 *    evitamos CORS do GitHub Releases e o MIME "text/plain" do jsDelivr.
 *
 *    Para usar offline / sem internet no servidor, baixe os 2 arquivos:
 *      stockfish-18-lite-single.js
 *      stockfish-18-lite-single.wasm
 *    de https://github.com/nmrugg/stockfish.js/releases/tag/v18.0.0
 *    e copie-os para src/main/resources/static/lib/stockfish/
 *
 *  - API pública (window.LocalEngine):
 *      const eng = new LocalEngine();
 *      await eng.init({ multiPv: 3, hashMb: 16 });
 *      eng.onUpdate   = ({depth, lines, final}) => { ... };
 *      eng.onBestmove = (uci) => { ... };
 *      eng.onError    = (msg) => { ... };
 *
 *      eng.startInfinite(fen);           // analisa até stop()
 *      eng.startDepth(fen, 22);          // analisa até profundidade
 *      eng.setMultiPv(5);
 *      eng.stop();
 *      eng.terminate();
 *
 *  - Formato de cada line emitida em onUpdate:
 *      { rank, depth, eval, mateIn, evalStr, uciMoves: [..] }
 *    'eval' é normalizado para a perspectiva das BRANCAS.
 * ============================================================ */
(function (global) {
  'use strict';

  // ── Configuração ────────────────────────────────────────────
  // Catálogo de engines disponíveis. Cada entrada vira uma opção do
  // dropdown "Engine" no frontend. Os arquivos vêm da release oficial
  // v18.0.0 do nmrugg/stockfish.js; a infra de carregamento é a mesma:
  //   1) /lib/stockfish/{filename}.js   (self-host, opcional)
  //   2) /cdn/stockfish/{filename}.js   (proxy backend, fallback automático)
  // Ambas são same-origin (servidas pelo Spring Boot), eliminando CORS
  // do GitHub e MIME inválido do jsDelivr.
  const ENGINE_CATALOG = {
    'lite-single': {
      label:        'Stockfish 18 lite (~7 MB, single-thread)',
      filename:     'stockfish-18-lite-single.js',
      multiThread:  false,
      description:  'Recomendado: rápido de baixar, força ELO ~3000+',
    },
    'full-single': {
      label:        'Stockfish 18 full (~108 MB, single-thread)',
      filename:     'stockfish-18-single.js',
      multiThread:  false,
      description:  'Mais forte; primeiro download lento (~108 MB)',
    },
    'lite-multi': {
      label:        'Stockfish 18 lite (~7 MB, multi-thread)',
      filename:     'stockfish-18-lite.js',
      multiThread:  true,
      description:  'Requer COOP/COEP no servidor (SharedArrayBuffer)',
    },
    'full-multi': {
      label:        'Stockfish 18 full (~108 MB, multi-thread)',
      filename:     'stockfish-18.js',
      multiThread:  true,
      description:  'Máxima força; requer COOP/COEP no servidor',
    },
  };
  const DEFAULT_ENGINE_KEY = 'lite-single';

  /**
   * Resolve qual URL usar para o build escolhido, priorizando self-host.
   */
  async function detectEngineUrl(engineKey) {
    const cfg = ENGINE_CATALOG[engineKey] || ENGINE_CATALOG[DEFAULT_ENGINE_KEY];
    const local = '/lib/stockfish/' + cfg.filename;
    const proxy = '/cdn/stockfish/' + cfg.filename;
    try {
      const r = await fetch(local, { method: 'HEAD' });
      if (r && r.ok) return local;
    } catch (_) { /* segue */ }
    return proxy;
  }

  /**
   * Cria o Worker carregando o script de Stockfish same-origin.
   *
   * Como ambas as URLs candidatas são same-origin (servidas pelo Spring Boot),
   * usamos {@code new Worker(url)} diretamente — sem o truque do Blob/
   * importScripts que era necessário para CDN externo. Isso evita problemas
   * de MIME-type strict checking (Chrome/Firefox modernos rejeitam scripts
   * cross-origin via importScripts quando o Content-Type não é JS).
   */
  function createWorker(engineUrl) {
    return new Worker(engineUrl);
  }

  // ── LocalEngine ─────────────────────────────────────────────
  class LocalEngine {
    constructor() {
      this.worker      = null;
      this.ready       = false;
      this.engineName  = 'Stockfish';
      this.engineUrl   = null;
      this.engineKey   = DEFAULT_ENGINE_KEY;

      // Estado da busca corrente
      this.fen           = null;
      this.multiPv       = 3;
      this.threads       = 1;
      this.hashMb        = 32;
      this.depth         = 0;            // maior depth visto na busca atual
      this.lines         = new Map();    // pvIndex -> {rank, depth, eval, mateIn, evalStr, uciMoves}
      this._searching    = false;
      this._pendingStart = null;         // {fen, kind:'infinite'|'depth', depth?} — substitui o anterior
      this._waiters      = [];
      this._emitTimer    = null;

      // Callbacks
      this.onUpdate    = null;
      this.onBestmove  = null;
      this.onError     = null;
    }

    /**
     * Inicializa o worker, faz handshake UCI e configura opções.
     * @param {{
     *   engine?:  string,    // chave do ENGINE_CATALOG (default 'lite-single')
     *   multiPv?: number,    // 1..5
     *   threads?: number,    // 1..N (apenas builds multi-thread)
     *   hashMb?:  number     // 1..1024 MB (memória da tabela de hash)
     * }} opts
     */
    async init(opts = {}) {
      if (this.worker) this.terminate();
      const engineKey = ENGINE_CATALOG[opts.engine] ? opts.engine : DEFAULT_ENGINE_KEY;
      const cfg       = ENGINE_CATALOG[engineKey];

      // Falha cedo com mensagem útil quando o navegador não tem SharedArrayBuffer
      if (cfg.multiThread && !LocalEngine.isMultiThreadSupported()) {
        throw new Error(
          'O build "' + engineKey + '" é multi-thread e requer SharedArrayBuffer ' +
          '(disponível apenas em páginas cross-origin isolated). ' +
          'Verifique se chess.cross-origin-isolation=true em application.properties ' +
          'e reinicie o servidor; depois recarregue esta página.');
      }

      const multiPv   = Math.max(1, Math.min(5,    (opts.multiPv | 0) || 3));
      const threads   = Math.max(1, Math.min(64,   (opts.threads | 0) || 1));
      const hashMb    = Math.max(1, Math.min(1024, (opts.hashMb  | 0) || 32));

      this.engineKey = engineKey;
      this.multiPv   = multiPv;
      this.threads   = threads;
      this.hashMb    = hashMb;

      this.engineUrl = await detectEngineUrl(engineKey);
      this.worker    = createWorker(this.engineUrl);

      this.worker.onmessage = e => this._handleLine(e.data);
      this.worker.onerror   = e => {
        const msg = (e && e.message) ? e.message : 'erro no worker do Stockfish';
        if (this.onError) try { this.onError(msg); } catch (_) {}
      };

      // Handshake UCI
      this._send('uci');
      await this._waitFor('uciok', 30000);

      // Opções (Threads só faz efeito em builds multi-thread + COOP/COEP)
      this._send('setoption name MultiPV value ' + this.multiPv);
      this._send('setoption name Hash value '    + this.hashMb);
      this._send('setoption name Threads value ' + this.threads);
      this._send('setoption name UCI_AnalyseMode value true');
      this._send('isready');
      await this._waitFor('readyok', 10000);
      this.ready = true;
    }

    /** Retorna metadados do build atual (label, multiThread, etc.). */
    getEngineInfo() {
      return Object.assign({ key: this.engineKey }, ENGINE_CATALOG[this.engineKey] || {});
    }

    /** Lista todas as engines disponíveis (para popular dropdowns). */
    static listEngines() {
      return Object.entries(ENGINE_CATALOG).map(([key, cfg]) =>
        Object.assign({ key }, cfg));
    }

    /**
     * Verifica se o navegador suporta builds multi-thread do Stockfish.
     *
     * <p>Stockfish multi-thread usa {@code SharedArrayBuffer}, que só fica
     * disponível quando a página está em modo <em>cross-origin isolated</em>
     * — e isso requer os headers HTTP COOP+COEP enviados pelo backend
     * (ver CrossOriginIsolationFilter no Spring Boot).</p>
     *
     * @returns true se SharedArrayBuffer + crossOriginIsolated == true
     */
    static isMultiThreadSupported() {
      try {
        const hasSAB = typeof SharedArrayBuffer !== 'undefined';
        const isolated = (typeof self.crossOriginIsolated === 'undefined')
                       ? hasSAB                            // navegador antigo: assume OK se tem SAB
                       : !!self.crossOriginIsolated;
        return hasSAB && isolated;
      } catch (_) { return false; }
    }

    /** Atualiza o número de linhas (MultiPV). */
    setMultiPv(n) {
      this.multiPv = Math.max(1, Math.min(5, n | 0));
      if (this.worker) this._send('setoption name MultiPV value ' + this.multiPv);
    }

    /** Atualiza o número de threads (UCI option Threads). */
    setThreads(n) {
      this.threads = Math.max(1, Math.min(64, n | 0));
      if (this.worker) this._send('setoption name Threads value ' + this.threads);
    }

    /** Atualiza o tamanho da tabela de hash em MB. */
    setHash(mb) {
      this.hashMb = Math.max(1, Math.min(1024, mb | 0));
      if (this.worker) this._send('setoption name Hash value ' + this.hashMb);
    }

    /** Analisa em modo infinito até stop() ou nova posição. */
    startInfinite(fen) {
      if (!this.ready) return;
      this._queueStart({ fen, kind: 'infinite' });
    }

    /** Analisa até atingir a profundidade indicada. */
    startDepth(fen, depth) {
      if (!this.ready) return;
      const d = Math.max(1, Math.min(60, depth | 0));
      this._queueStart({ fen, kind: 'depth', depth: d });
    }

    /** Para a busca corrente (engine continua viva). */
    stop() {
      this._pendingStart = null;
      if (this.worker && this._searching) this._send('stop');
    }

    /** Encerra o worker. */
    terminate() {
      if (this.worker) {
        try { this._send('quit'); } catch (_) {}
        try { this.worker.terminate(); } catch (_) {}
      }
      this.worker        = null;
      this.ready         = false;
      this._searching    = false;
      this._pendingStart = null;
      this._waiters      = [];
      if (this._emitTimer) { clearTimeout(this._emitTimer); this._emitTimer = null; }
    }

    // ── Internos ────────────────────────────────────────────────

    /**
     * Enfileira uma nova busca substituindo qualquer pendente.
     * Se já há busca em andamento, envia 'stop' e a próxima é disparada
     * automaticamente pelo handler de 'bestmove' (evitando race-conditions
     * em navegação rápida pelo tabuleiro).
     */
    _queueStart(req) {
      this._pendingStart = req;
      if (this._searching) {
        this._send('stop');
        // safety: dispara mesmo sem bestmove dentro de 1,5s
        clearTimeout(this._safetyTimer);
        this._safetyTimer = setTimeout(() => {
          if (this._pendingStart) {
            this._searching = false;
            this._launchPending();
          }
        }, 1500);
      } else {
        this._launchPending();
      }
    }

    _launchPending() {
      clearTimeout(this._safetyTimer);
      if (!this._pendingStart || this._searching) return;
      const req = this._pendingStart;
      this._pendingStart = null;
      this._beginSearch(req.fen);
      if (req.kind === 'infinite') this._send('go infinite');
      else                          this._send('go depth ' + req.depth);
    }

    _beginSearch(fen) {
      this.fen        = fen;
      this.depth      = 0;
      this.lines.clear();
      this._searching = true;
      this._send('position fen ' + fen);
    }

    _send(cmd) {
      if (!this.worker) return;
      this.worker.postMessage(cmd);
    }

    _waitFor(token, timeoutMs) {
      return new Promise((resolve, reject) => {
        let done = false;
        const t = setTimeout(() => {
          if (done) return; done = true;
          this._waiters = this._waiters.filter(w => w !== handler);
          reject(new Error('Timeout aguardando ' + token));
        }, timeoutMs);
        const handler = (line) => {
          if (done) return;
          if (line && line.indexOf(token) === 0) {
            done = true; clearTimeout(t);
            this._waiters = this._waiters.filter(w => w !== handler);
            resolve(line);
          }
        };
        this._waiters.push(handler);
      });
    }

    _handleLine(line) {
      if (!line || typeof line !== 'string') return;

      // Erros propagados pelo wrapper do worker
      if (line.indexOf('__error__:') === 0) {
        const msg = line.substring('__error__:'.length);
        if (this.onError) try { this.onError(msg); } catch (_) {}
        return;
      }

      // Despacha para handlers de espera (uciok, readyok, ...)
      if (this._waiters.length) {
        const ws = this._waiters.slice();
        for (const w of ws) {
          try { w(line); } catch (_) {}
        }
      }

      if (line.indexOf('info ') === 0 && line.indexOf(' pv ') >= 0 && this._searching) {
        this._parseInfo(line);
      }
      else if (line.indexOf('bestmove') === 0) {
        const bm = line.split(/\s+/)[1] || '';
        this._searching = false;
        if (this.onBestmove) { try { this.onBestmove(bm); } catch (_) {} }

        if (this._pendingStart) {
          // Outra busca foi solicitada enquanto esta terminava — descarta
          // o resultado parcial e dispara a próxima.
          this._launchPending();
        } else {
          // Busca terminou naturalmente (depth atingida) ou stop manual.
          this._emitUpdate(true);
        }
      }
      else if (line.indexOf('id name ') === 0) {
        this.engineName = line.substring(8).trim();
      }
    }

    _parseInfo(line) {
      const tokens = line.split(/\s+/);
      let depth = null, multipv = 1;
      let scoreType = null, scoreVal = null;
      let pv = null;

      for (let i = 1; i < tokens.length; i++) {
        const t = tokens[i];
        if (t === 'depth')         depth   = parseInt(tokens[++i], 10);
        else if (t === 'multipv')  multipv = parseInt(tokens[++i], 10);
        else if (t === 'score') {
          scoreType = tokens[++i];
          scoreVal  = parseInt(tokens[++i], 10);
          // pula 'lowerbound'/'upperbound' se vierem
          if (tokens[i + 1] === 'lowerbound' || tokens[i + 1] === 'upperbound') i++;
        }
        else if (t === 'pv') {
          pv = tokens.slice(i + 1);
          break;
        }
      }

      if (depth == null || pv == null || pv.length === 0) return;

      // Normaliza a avaliação para a perspectiva das brancas
      const sideToMoveWhite = this.fen ? (this.fen.split(' ')[1] === 'w') : true;
      let evalCp = null, mateIn = null, evalStr = '0.00';

      if (scoreType === 'cp' && Number.isFinite(scoreVal)) {
        const cp = sideToMoveWhite ? scoreVal : -scoreVal;
        evalCp  = cp / 100;
        evalStr = (evalCp >= 0 ? '+' : '') + evalCp.toFixed(2);
      } else if (scoreType === 'mate' && Number.isFinite(scoreVal)) {
        const m = sideToMoveWhite ? scoreVal : -scoreVal;
        mateIn  = m;
        evalStr = (m > 0 ? '+M' : '-M') + Math.abs(m);
      }

      // Descarta info de profundidade menor para o mesmo multipv
      const existing = this.lines.get(multipv);
      if (existing && existing.depth > depth) return;

      this.lines.set(multipv, {
        rank:     multipv,
        depth:    depth,
        eval:     evalCp,
        mateIn:   mateIn,
        evalStr:  evalStr,
        uciMoves: pv,
      });
      if (depth > this.depth) this.depth = depth;

      this._emitUpdate(false);
    }

    _emitUpdate(final) {
      if (!this.onUpdate) return;
      if (final) {
        if (this._emitTimer) { clearTimeout(this._emitTimer); this._emitTimer = null; }
        this._doEmit(true);
        return;
      }
      // throttle de ~8 atualizações/s para não sobrecarregar a UI
      if (this._emitTimer) return;
      this._emitTimer = setTimeout(() => {
        this._emitTimer = null;
        this._doEmit(false);
      }, 120);
    }

    _doEmit(final) {
      const sorted = Array.from(this.lines.values()).sort((a, b) => a.rank - b.rank);
      try {
        this.onUpdate({
          fen:   this.fen,
          depth: this.depth,
          lines: sorted,
          final: !!final,
        });
      } catch (e) {
        console.error('LocalEngine onUpdate error:', e);
      }
    }
  }

  // Exporta no escopo global
  global.LocalEngine = LocalEngine;
})(typeof window !== 'undefined' ? window : self);
