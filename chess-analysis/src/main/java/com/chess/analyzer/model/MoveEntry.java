package com.chess.analyzer.model;

import java.util.List;
import java.util.Locale;

/**
 * Representa um meio-lance (ply) de uma partida de xadrez.
 * Os campos de análise (eval, bestMove, pv) são preenchidos
 * assincronamente pelo StockfishPoolService.
 */
public class MoveEntry {

    // ── Dados do lance ──────────────────────────────────────────
    private final String  uci;          // ex: "e2e4", "g1f3", "e7e8q"
    private final String  san;          // ex: "e4", "Nf3", "e8=Q"
    private final String  fenBefore;    // FEN antes do lance
    private final String  fenAfter;     // FEN depois do lance
    private final int     moveNumber;   // número do lance (brancas e pretas compartilham)
    private final boolean whiteTurn;    // true = lance das brancas

    // ── Resultados da análise (voláteis — escrita em virtual thread) ──
    private volatile Double       eval;       // centipeões / 100, perspectiva Brancas
    private volatile Integer      mateIn;     // mate em N, null se não forçado
    private volatile String       bestMove;   // melhor lance UCI sugerido
    private volatile List<String> pv;         // variante principal (lista de UCI)
    private volatile boolean      analyzed;

    public MoveEntry(String uci, String san, String fenBefore, String fenAfter,
                     int moveNumber, boolean whiteTurn) {
        this.uci        = uci;
        this.san        = san;
        this.fenBefore  = fenBefore;
        this.fenAfter   = fenAfter;
        this.moveNumber = moveNumber;
        this.whiteTurn  = whiteTurn;
        this.analyzed   = false;
    }

    public void setAnalysis(Double eval, Integer mateIn, String bestMove, List<String> pv) {
        this.eval     = eval;
        this.mateIn   = mateIn;
        this.bestMove = bestMove;
        this.pv       = pv;
        this.analyzed = true;
    }

    // ── Getters ─────────────────────────────────────────────────
    public String  getUci()       { return uci; }
    public String  getSan()       { return san; }
    public String  getFenBefore() { return fenBefore; }
    public String  getFenAfter()  { return fenAfter; }
    public int     getMoveNumber(){ return moveNumber; }
    public boolean isWhiteTurn()  { return whiteTurn; }
    public Double  getEval()      { return eval; }
    public Integer getMateIn()    { return mateIn; }
    public String  getBestMove()  { return bestMove; }
    public List<String> getPv()   { return pv; }
    public boolean isAnalyzed()   { return analyzed; }

    /** Formatação amigável: "+0.28", "-1.35", "+M3", "-M5", "?" */
    public String getEvalFormatted() {
        if (!analyzed) return "?";
        if (mateIn != null) return (mateIn > 0 ? "+M" : "-M") + Math.abs(mateIn);
        if (eval   == null) return "?";
        // Locale.ROOT garante ponto decimal independente da JVM locale (ex: pt-BR usa vírgula)
        return String.format(Locale.ROOT, "%+.2f", eval);
    }
}
