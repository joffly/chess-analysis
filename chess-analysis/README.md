# Chess Analyzer
**Spring Boot 4.0 + Java 25 + Stockfish UCI Multi-Thread**

Aplicação web moderna para carregar partidas de xadrez em formato PGN, analisá-las com o motor Stockfish e navegar pelos lances com barra de avaliação em tempo real. Construída com **virtual threads (Project Loom)** e **pool de múltiplas instâncias Stockfish** para análise paralela de alta performance.

---

## Funcionalidades

| Recurso | Detalhe |
|---|---|
| Carregamento PGN | Suporte a arquivos com N partidas |
| Análise Stockfish | UCI completo, depth configurável, progresso em tempo real via SSE |
| **Análise Multi-Thread** | Pool configurável de instâncias Stockfish (1-8) para análise paralela |
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
chess.stockfish-pool-size=4  # Número de instâncias para análise paralela (1-8)
```

**Opção B: Via interface da aplicação**
- Cole o caminho do executável do Stockfish
- Ajuste a profundidade (15 = bom equilíbrio)
- Ajuste o tamanho do pool (4 = recomendado para CPUs quad-core+)
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
│   ├── StockfishPoolService       Gerenciador do pool de processos UCI (multi-thread)
│   ├── StockfishService           Gerenciador single-thread (legado, manter compatibilidade)
│   ├── BoardService               Validação de lances e geração de movimentos legais
│   ├── PgnService                 Parser PGN (chesslib) e exportação anotada
│   └── GameAnalysisService        Orquestrador de análise (virtual thread + SSE + parallel stream)
└── controller/
    └── GameController             REST API + SSE + download de PGN
```

### Protocolo UCI (fluxo de análise por posição)

```
→ uci
← uciok
→ setoption name Hash value 128
→ setoption name Threads value 1   # Cada instância no pool usa 1 thread
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
O `StockfishPoolService` normaliza para a **perspectiva das Brancas**
(positivo = Brancas melhor) para exibição consistente na barra.

### Virtual Threads + Pool Multi-Thread (Java 25 — Project Loom)

A análise utiliza duas camadas de paralelismo:

1. **Virtual Threads**: Cada lance é processado em uma virtual thread independente, permitindo milhares de tarefas concorrentes com mínimo overhead de memória.

2. **Pool de Instâncias Stockfish**: Múltiplos processos Stockfish independentes (configurável via `chess.stockfish-pool-size`) permitem análise verdadeiramente paralela de posições diferentes.

```java
// Exemplo: 4 instâncias analisam 4 lances simultaneamente
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ Stockfish #0│  │ Stockfish #1│  │ Stockfish #2│  │ Stockfish #3│
│   Lance 1   │  │   Lance 2   │  │   Lance 3   │  │   Lance 4   │
└─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘
       ↑                ↑                ↑                ↑
       └────────────────┴────────────────┴────────────────┘
                    Virtual Threads (CompletableFuture)
```

**Benefícios:**
- Até 4x mais rápido em CPUs quad-core+
- Escalabilidade linear com número de núcleos
- Uso eficiente de recursos (cada instância usa 1 thread CPU + 128MB Hash)

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
