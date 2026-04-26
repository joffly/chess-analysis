package com.chess.analyzer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chess.analyzer.model.GameData;
import com.chess.analyzer.model.MoveEntry;

@DisplayName("PgnService Tests")
class PgnServiceTest {

	private PgnService pgnService;
	private static final Logger log = LoggerFactory.getLogger(PgnServiceTest.class);

	@BeforeEach
	void setUp() {
		pgnService = new PgnService();
	}

	@Test
	@DisplayName("Deve carregar partida From Position com FEN customizada e 27 lances")
	void testLoadItalianGameFromPosition() throws Exception {
		// Arrange
		File pgn = new File("src/test/resources/italian_game_from_position.pgn");
		assertTrue(pgn.exists(), "Arquivo PGN não encontrado: " + pgn.getAbsolutePath());

		// Act
		List<GameData> games = pgnService.load(pgn);

		// Assert
		assertNotNull(games, "Lista de partidas não deve ser null");
		assertEquals(1, games.size(), "Deve carregar 1 partida");

		GameData game = games.get(0);

		// Verifica tags
		assertEquals("Italian Game SuperBlitz Arena", game.getTags().get("Event"));
		assertEquals("Ugtakhbayaraa", game.getTags().get("White"));
		assertEquals("fjoffly", game.getTags().get("Black"));
		assertEquals("1-0", game.getTags().get("Result"));
		assertEquals("From Position", game.getTags().get("Variant"));

		// Verifica FEN
		String expectedFen = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 1";
		assertEquals(expectedFen, game.getInitialFen(), "FEN customizado deve corresponder ao do arquivo");

		// Verifica 27 lances
		int expectedMoves = 27;
		assertEquals(expectedMoves, game.getMoves().size(),
				"Deve ter " + expectedMoves + " lances, mas tem " + game.getMoves().size());

		// Verifica alguns lances específicos
		assertEquals("d3", game.getMoves().get(0).getSan(), "1º lance deve ser d3");
		assertEquals("d6", game.getMoves().get(1).getSan(), "2º lance deve ser d6");
		assertEquals("Qf6", game.getMoves().get(23).getSan(), "24º lance deve ser Qf6");
		assertEquals("Bg6", game.getMoves().get(25).getSan(), "26º lance deve ser Bg6");
		assertEquals("Qxf6", game.getMoves().get(26).getSan(), "27º lance (final) deve ser Qxf6");

		// Verifica alternância de turnos
		for (int i = 0; i < game.getMoves().size(); i++) {
			boolean expectedWhite = (i % 2 == 0);
			assertEquals(expectedWhite, game.getMoves().get(i).isWhiteTurn(),
					"Lance " + (i + 1) + " deve ser " + (expectedWhite ? "branco" : "preto"));
		}
	}

	@Test
	@DisplayName("Deve carregar todos os 27 lances corretamente")
	void testLoadAllMovesFromPosition() throws Exception {
		// Arrange
		File pgn = new File("src/test/resources/italian_game_from_position.pgn");

		// Act
		List<GameData> games = pgnService.load(pgn);
		GameData game = games.get(0);

		// Assert - deve ter exatamente 27 lances
		assertEquals(27, game.getMoves().size(), "Partida deve ter 27 lances, mas tem " + game.getMoves().size());
	}

	@Test
	@DisplayName("Deve calcular números de lances corretamente em From Position")
	void testMoveNumbersInFromPosition() throws Exception {
		// Arrange
		File pgn = new File("src/test/resources/italian_game_from_position.pgn");

		// Act
		List<GameData> games = pgnService.load(pgn);
		GameData game = games.get(0);

		// Assert - moveNumber deve começar em 1 (não em 0)
		if (game.getMoves().size() >= 27) {
			assertEquals(1, game.getMoves().get(0).getMoveNumber(), "1º lance (branco) = movimento 1");
			assertEquals(1, game.getMoves().get(1).getMoveNumber(), "2º lance (preto) = movimento 1");
			assertEquals(2, game.getMoves().get(2).getMoveNumber(), "3º lance (branco) = movimento 2");
			assertEquals(2, game.getMoves().get(3).getMoveNumber(), "4º lance (preto) = movimento 2");
			assertEquals(14, game.getMoves().get(26).getMoveNumber(), "27º lance (branco) = movimento 14");
		}
	}

	@Test
	@DisplayName("Deve ter FEN válido em cada posição")
	void testFenPositionsValid() throws Exception {
		// Arrange
		File pgn = new File("src/test/resources/italian_game_from_position.pgn");

		// Act
		List<GameData> games = pgnService.load(pgn);
		GameData game = games.get(0);

		// Assert - cada movimento deve ter FEN antes e depois válidos
		for (int i = 0; i < game.getMoves().size(); i++) {
			var move = game.getMoves().get(i);
			assertNotNull(move.getFenBefore(), "FEN antes do lance " + (i + 1) + " não deve ser null");
			assertNotNull(move.getFenAfter(), "FEN depois do lance " + (i + 1) + " não deve ser null");
			assertFalse(move.getFenBefore().isBlank(), "FEN antes do lance " + (i + 1) + " não deve estar vazio");
			assertFalse(move.getFenAfter().isBlank(), "FEN depois do lance " + (i + 1) + " não deve estar vazio");

			log.debug("Lance {}: {} -> FEN após: {}", i + 1, move.getSan(), move.getFenAfter());
		}
	}



	@Test
	@DisplayName("VALIDAÇÃO: Testa carregamento completo e válido de 27 lances")
	void validateCompleteGameLoading() throws Exception {
		File pgn = new File("src/test/resources/italian_game_from_position.pgn");
		assertTrue(pgn.exists(), "Arquivo PGN não encontrado");

		List<GameData> games = pgnService.load(pgn);
		assertEquals(1, games.size(), "Deve ter 1 partida");

		GameData game = games.get(0);
		int totalMoves = game.getMoves().size();

		log.info("===== VALIDAÇÃO COMPLETA DA PARTIDA =====");
		log.info("Total de lances carregados: {}", totalMoves);

		// 1. Valida que tem exatamente 27 lances
		assertEquals(27, totalMoves, "❌ FALHA: Deve ter 27 lances, tem " + totalMoves);
		log.info("✓ Teste 1: Carregados 27 lances");

		// 2. Valida que não há null em nenhum lance
		for (int i = 0; i < totalMoves; i++) {
			MoveEntry move = game.getMoves().get(i);
			assertNotNull(move, "Lance " + (i + 1) + " é null");
			assertNotNull(move.getSan(), "SAN do lance " + (i + 1) + " é null");
			assertNotNull(move.getUci(), "UCI do lance " + (i + 1) + " é null");
			assertNotNull(move.getFenBefore(), "FEN antes do lance " + (i + 1) + " é null");
			assertNotNull(move.getFenAfter(), "FEN após do lance " + (i + 1) + " é null");
		}
		log.info("✓ Teste 2: Todos os 27 lances têm dados válidos (sem null)");

		// 3. Valida alternância de turnos
		for (int i = 0; i < totalMoves; i++) {
			boolean expectedWhite = (i % 2 == 0);
			boolean actualWhite = game.getMoves().get(i).isWhiteTurn();
			assertEquals(expectedWhite, actualWhite, "Lance " + (i + 1) + ": esperado "
					+ (expectedWhite ? "branco" : "preto") + ", obtido " + (actualWhite ? "branco" : "preto"));
		}
		log.info("✓ Teste 3: Alternância de turnos correta");

		// 4. Valida numeração de movimentos
		for (int i = 0; i < totalMoves; i++) {
			int expectedMoveNum = (i / 2) + 1;
			int actualMoveNum = game.getMoves().get(i).getMoveNumber();
			assertEquals(expectedMoveNum, actualMoveNum, "Lance " + (i + 1) + ": número de movimento incorreto");
		}
		log.info("✓ Teste 4: Numeração de movimentos correta");

		// 5. Valida lances específicos
		String[] expectedSans = { "d3", // Lance 1
				"d6", // Lance 2
				"Nc3", // Lance 3
				"Nf6", // Lance 4
				"Qf6", // Lance 24
				"Be3", // Lance 25
				"Bg6", // Lance 26
				"Qxf6" // Lance 27
		};
		int[] lanceIndices = { 0, 1, 2, 3, 23, 24, 25, 26 };

		for (int i = 0; i < expectedSans.length; i++) {
			int idx = lanceIndices[i];
			String expectedSan = expectedSans[i];
			String actualSan = game.getMoves().get(idx).getSan();
			assertEquals(expectedSan, actualSan,
					"Lance " + (idx + 1) + ": SAN incorreto. Esperado: " + expectedSan + ", Obtido: " + actualSan);
		}
		log.info("✓ Teste 5: Lances específicos validados");

		// 6. Valida FEN inicial
		String expectedFen = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 1";
		assertEquals(expectedFen, game.getInitialFen(), "FEN inicial incorreto");
		log.info("✓ Teste 6: FEN inicial correto");

		// 7. Valida FENs sequenciais (cada FEN após deve ser FEN antes do próximo)
		for (int i = 0; i < totalMoves - 1; i++) {
			String fenAfter = game.getMoves().get(i).getFenAfter();
			String fenBefore = game.getMoves().get(i + 1).getFenBefore();
			assertEquals(fenAfter, fenBefore, "Descontinuidade de FEN entre lances " + (i + 1) + " e " + (i + 2));
		}
		log.info("✓ Teste 7: Continuidade de FENs validada");

		// 8. Log detalhado dos lances
		log.info("");
		log.info("Lances carregados:");
		for (int i = 0; i < totalMoves; i++) {
			MoveEntry m = game.getMoves().get(i);
			log.info("  Lance {:2d}: {} ({}) - Move #{}", i + 1, m.getSan(), m.getUci(), m.getMoveNumber());
		}

		log.info("");
		log.info("===== ✓ TODAS AS VALIDAÇÕES PASSARAM =====");
	}

	@Test
	@DisplayName("VALIDAÇÃO: Testa que FEN inicial gera movimentos legais corretos")
	void validateInitialFenMovesLegal() throws Exception {
		String initialFen = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 1";

		com.github.bhlangonijr.chesslib.Board board = new com.github.bhlangonijr.chesslib.Board();
		board.loadFromFen(initialFen);

		log.info("===== VALIDAÇÃO DE MOVIMENTOS LEGAIS =====");
		log.info("FEN: {}", initialFen);

		// Testa se d3 é legal
		com.github.bhlangonijr.chesslib.move.MoveList legalMoves = new com.github.bhlangonijr.chesslib.move.MoveList(
				initialFen);

		log.info("Total de lances legais na posição inicial: {}", legalMoves.size());

		boolean found_d3 = false;
		for (com.github.bhlangonijr.chesslib.move.Move move : legalMoves) {
			com.github.bhlangonijr.chesslib.Board testBoard = new com.github.bhlangonijr.chesslib.Board();
			testBoard.loadFromFen(initialFen);
			if (testBoard.doMove(move)) {
				com.github.bhlangonijr.chesslib.move.MoveList ml = new com.github.bhlangonijr.chesslib.move.MoveList(
						initialFen);
				ml.add(move);
				String[] sanArray = ml.toSanArray();
				if (sanArray != null && sanArray.length > 0 && "d3".equals(sanArray[0])) {
					found_d3 = true;
					log.info("✓ Lance d3 encontrado e é legal");
					break;
				}
			}
		}

		assertTrue(found_d3, "❌ FALHA: Lance d3 não encontrado como legal na posição inicial!");
	}

	@Test
	@DisplayName("VALIDAÇÃO: Teste de integridade estrutural dos dados")
	void validateDataIntegrity() throws Exception {
		File pgn = new File("src/test/resources/italian_game_from_position.pgn");
		List<GameData> games = pgnService.load(pgn);
		GameData game = games.get(0);

		log.info("===== VALIDAÇÃO DE INTEGRIDADE ESTRUTURAL =====");

		// Verifica tags essenciais
		assertNotNull(game.getTags(), "Tags não deve ser null");
		assertTrue(game.getTags().containsKey("Event"), "Falta tag Event");
		assertTrue(game.getTags().containsKey("White"), "Falta tag White");
		assertTrue(game.getTags().containsKey("Black"), "Falta tag Black");
		assertTrue(game.getTags().containsKey("Result"), "Falta tag Result");
		assertTrue(game.getTags().containsKey("FEN"), "Falta tag FEN");
		log.info("✓ Tags essenciais presentes");

		// Verifica valores dos tags
		assertEquals("Ugtakhbayaraa", game.getTags().get("White"), "White incorreto");
		assertEquals("fjoffly", game.getTags().get("Black"), "Black incorreto");
		assertEquals("1-0", game.getTags().get("Result"), "Result incorreto");
		log.info("✓ Valores dos tags corretos");

		// Verifica lista de movimentos
		assertNotNull(game.getMoves(), "Lista de movimentos não deve ser null");
		assertTrue(game.getMoves().size() > 0, "Lista de movimentos está vazia");
		assertTrue(game.getMoves().size() == 27, "Deve ter 27 movimentos");
		log.info("✓ Lista de movimentos válida com 27 entradas");

		log.info("===== ✓ INTEGRIDADE ESTRUTURAL CONFIRMADA =====");
	}

	@Test
	@DisplayName("VALIDAÇÃO: Teste de coerência de FEN e movimentos")
	void validateFenMovementCoherence() throws Exception {
		File pgn = new File("src/test/resources/italian_game_from_position.pgn");
		List<GameData> games = pgnService.load(pgn);
		GameData game = games.get(0);

		log.info("===== VALIDAÇÃO DE COERÊNCIA FEN-MOVIMENTO =====");

		com.github.bhlangonijr.chesslib.Board board = new com.github.bhlangonijr.chesslib.Board();
		board.loadFromFen(game.getInitialFen());

		for (int i = 0; i < game.getMoves().size(); i++) {
			MoveEntry move = game.getMoves().get(i);
			String currentFen = board.getFen();
			String expectedFenBefore = move.getFenBefore();

			// Verifica se o FEN antes corresponde
			assertEquals(expectedFenBefore, currentFen, "Lance " + (i + 1) + ": FEN antes não corresponde");

			// Procura e executa o movimento
			com.github.bhlangonijr.chesslib.move.MoveList legalMoves = new com.github.bhlangonijr.chesslib.move.MoveList(
					currentFen);

			com.github.bhlangonijr.chesslib.move.Move executedMove = null;
			for (com.github.bhlangonijr.chesslib.move.Move m : legalMoves) {
				com.github.bhlangonijr.chesslib.Board testBoard = new com.github.bhlangonijr.chesslib.Board();
				testBoard.loadFromFen(currentFen);
				if (testBoard.doMove(m) && move.getUci().equals(m.toString())) {
					executedMove = m;
					break;
				}
			}

			assertNotNull(executedMove, "Lance " + (i + 1) + " (" + move.getUci() + ") não é legal nesta posição");

			assertTrue(board.doMove(executedMove), "Falha ao executar lance " + (i + 1));

			String expectedFenAfter = move.getFenAfter();
			String actualFenAfter = board.getFen();

			assertEquals(expectedFenAfter, actualFenAfter, "Lance " + (i + 1) + ": FEN após não corresponde");

			if ((i + 1) % 5 == 0) {
				log.info("✓ Lances 1-{} validados", i + 1);
			}
		}

		log.info("===== ✓ COERÊNCIA FEN-MOVIMENTO CONFIRMADA PARA 27 LANCES =====");
	}
}
