# Chess Analyzer
**Spring Boot 4.0 + Java 25 + Stockfish UCI**

Aplicação web moderna para carregar partidas de xadrez em formato PGN, analisá-las com o motor Stockfish e navegar pelos lances com barra de avaliação em tempo real. Construída com virtual threads (Project Loom) para máxima performance.

---

## Funcionalidades

| Recurso | Detalhe |
|---|---|
| Carregamento PGN | Suporte a arquivos com N partidas |
| Análise Stockfish | UCI completo, depth configurável, progresso em tempo real via SSE |
| Tabuleiro interativo | chessboard.js, drag-and-drop, clique, peças Wikipedia |
| Movimentação livre | Arraste ou clique — com validação legal server-side (chesslib) |
| Roque / En-passant | Tratados automaticamente pelo chesslib |
| Promoção de peão | Modal para escolha da peça (Dama / Torre / Bispo / Cavalo) |
| Barra de avaliação | Animação suave, escala ±5 peões, suporte a mate |
| Lista de lances | SAN + eval por lance, clicável, destaque do lance atual |
| Navegação | Botões, atalhos de teclado (←→ Home End F) |
| Exportação | PGN anotado com `[%eval ±X.XX]` (compatível Lichess / Scid / Arena) |

---

## Pré-requisitos

| Ferramenta | Versão |
|---|---|
| Java | **25** (Project Loom — virtual threads, switch expressions) |
| Maven | 3.9+ |
| Stockfish | 15+ recomendado |

**Download do Stockfish:** https://stockfishchess.org/download/

> ℹ️ **Nota importante:** Você precisa ter o Stockfish instalado separadamente. A aplicação se conecta a ele via protocolo UCI.

---

## Como executar

### Opção 1: Executar com Maven (desenvolvimento)
```bash
mvn clean spring-boot:run
```

### Opção 2: Gerar JAR e executar
```bash
mvn clean package -DskipTests
java -jar target/chess-analyzer-1.0.0.jar
```

A aplicação estará disponível em: **http://localhost:8080**

### Configurar Stockfish

**Opção A: Via arquivo `application.properties`**
```properties
chess.stockfish-path=C:/stockfish/stockfish-windows-x86-64.exe
chess.analysis-depth=15
chess.analysis-time-limit-ms=5000
```

**Opção B: Via interface da aplicação**
- Cole o caminho do executável do Stockfish
- Ajuste a profundidade (15 = bom equilíbrio)
- Clique **Conectar**

---

## Uso da interface

1. **Conectar Stockfish** — cole o caminho para o executável, ajuste a profundidade (15 = bom) e clique **Conectar**
2. **Abrir PGN** — carregue um arquivo `.pgn` com uma ou mais partidas
3. **Navegar** — clique em uma partida na lista lateral; use os botões ← → ou as teclas
4. **Analisar** — clique **Analisar**; veja o progresso e as avaliações aparecendo em tempo real
5. **Jogar livre** — troque para o modo **✏️ Jogar livre** e movimente as peças por drag-and-drop ou clique
6. **Exportar** — clique **Exportar PGN Anotado** para baixar o arquivo com avaliações

### Atalhos de teclado (modo Navegação)

| Tecla | Ação |
|---|---|
| `←` | Lance anterior |
| `→` | Próximo lance |
| `Home` | Posição inicial |
| `End` | Posição final |
| `F` | Girar tabuleiro |

---

## Arquitetura

```
src/main/java/com/chess/analyzer/
├── ChessAnalyzerApplication       Entry point — ativa virtual threads (Java 25)
├── config/
│   └── AppProperties              @ConfigurationProperties (chess.*, stockfish-path)
├── model/
│   ├── MoveEntry                  Representação de um lance com avaliação
│   ├── GameData                   Partida completa com dados de análise
│   ├── GameSummary                DTO imutável (record) — resumo da partida
│   ├── StockfishResult            Record imutável — avaliação UCI retornada
│   ├── LegalMovesResponse         DTO: destinos legais agrupados por peça
│   ├── MoveRequest                DTO: lance enviado pelo frontend (from/to/promotion)
│   └── MoveResponse               DTO: resultado do lance (FEN, SAN, isCheckmate, etc.)
├── service/
│   ├── StockfishService           Gerenciador do processo UCI (singleton, synchronized)
│   ├── BoardService               Validação de lances e geração de movimentos legais
│   ├── PgnService                 Parser PGN (chesslib) e exportação anotada
│   └── GameAnalysisService        Orquestrador de análise (virtual thread + SSE)
└── controller/
    └── GameController             REST API + SSE + download de PGN
```

### Protocolo UCI (fluxo de análise por posição)

```
→ uci
← uciok
→ setoption name Hash value 256
→ setoption name Threads value 2
→ isready
← readyok
── para cada posição: ──
→ position fen rnbqkbnr/.../w KQkq - 0 1
→ go depth 15
← info depth 1 seldepth 1 score cp 13 ...
← info depth 15 seldepth 22 score cp 45 pv e2e4 e7e5 g1f3 ...
← bestmove e2e4 ponder e7e5
```

### Normalização de avaliação

O Stockfish retorna a avaliação do **lado que vai jogar**.
O `StockfishService` normaliza para a **perspectiva das Brancas**
(positivo = Brancas melhor) para exibição consistente na barra.

### Virtual Threads (Java 25 — Project Loom)

O loop de análise roda em `Thread.ofVirtual()` — ideal para I/O-bound bloqueante
com o processo Stockfish. O Tomcat também usa virtual threads via
`spring.threads.virtual.enabled=true`, permitindo milhares de conexões simultâneas
com mínimo overhead de memória.

---

## Dependências principais

| Biblioteca | Versão | Uso |
|---|---|---|
| Spring Boot | 4.0.6 | Framework web, REST, SSE |
| Spring Web | 7.0.7 | Servidor MVC |
| Spring Thymeleaf | 4.0.6 | Template engine HTML |
| chesslib | 1.3.6 | Parse PGN, replay, FEN, movimentos legais |
| JUnit 6 | 6.0.3 | Testes unitários |
| Mockito | 5.20.0 | Mocking para testes |
| chessboard.js | 1.0.0 | Renderização do tabuleiro (CDN) |
| Bootstrap 5 | 5.3+ | UI (CDN) |
