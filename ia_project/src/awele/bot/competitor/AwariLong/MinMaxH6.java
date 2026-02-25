package awele.bot.competitor.AwariLong;

import awele.bot.CompetitorBot;
import awele.core.Board;
import awele.core.InvalidBotException;

import java.util.Arrays;

/*
public class MinMaxH6 extends CompetitorBot {

    // --- PARAMÈTRES DE TEMPS ---
    private static final long TIME_LIMIT_MS = 90;
    private boolean timeOut = false;
    private int nodeCount = 0;

    public MinMaxH6() throws InvalidBotException {
        this.setBotName("MinMaxAwariLong");
        this.addAuthor("Iliass FERCHACH");
    }

    @Override
    public void initialize() {
    }

    @Override
    public void finish() {
    }

    @Override
    public void learn() {
    }

    @Override
    public double[] getDecision(Board board) {
        int currentPlayer = board.getCurrentPlayer();
        int opponentPlayer = Board.otherPlayer(currentPlayer);

        // --- 1. OUVERTURE FORCÉE ---
        if (board.getLog(currentPlayer).length == 0 && board.getLog(opponentPlayer).length == 0) {
            double[] firstMoveDecision = new double[Board.NB_HOLES];
            Arrays.fill(firstMoveDecision, Double.NEGATIVE_INFINITY);
            if (board.validMoves(currentPlayer)[5]) {
                firstMoveDecision[5] = 1.0;
                return firstMoveDecision;
            }
        }

        // --- 2. INITIALISATION ---
        double[] bestDecisions = new double[Board.NB_HOLES];
        Arrays.fill(bestDecisions, Double.NEGATIVE_INFINITY);

        long startTime = System.currentTimeMillis();
        this.timeOut = false;
        this.nodeCount = 0;

        // On crée notre plateau ultra-léger basé sur les bits
        AwariLong fastRoot = AwariLong.fromBoard(board);

        // --- 3. APPROFONDISSEMENT ITÉRATIF ---
        for (int depth = 1; depth <= 50; depth++) {
            double[] currentDecisions = new double[Board.NB_HOLES];
            Arrays.fill(currentDecisions, Double.NEGATIVE_INFINITY);
            boolean searchCompleted = true;

            // --- TRI PRIMITIF ---
            int[] moveOrder = {0, 1, 2, 3, 4, 5};
            if (depth > 1) {
                for (int i = 0; i < 6; i++) {
                    for (int j = i + 1; j < 6; j++) {
                        if (bestDecisions[moveOrder[j]] > bestDecisions[moveOrder[i]]) {
                            int temp = moveOrder[i];
                            moveOrder[i] = moveOrder[j];
                            moveOrder[j] = temp;
                        }
                    }
                }
            }

            // --- SIMULATION ---
            for (int holeIndex = 0; holeIndex < 6; holeIndex++) {
                int i = moveOrder[holeIndex];

                if (fastRoot.isValidMove(currentPlayer, i)) {
                    //On clone le plateau AVANT de jouer pour ne pas casser la racine
                    AwariLong nextState = new AwariLong(fastRoot);
                    nextState.playMove(i);

                    // On lance minimax (le prochain coup c'est l'adversaire, donc maximizing = false)
                    double val = minimax(nextState, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, currentPlayer, startTime);

                    if (timeOut) {
                        searchCompleted = false;
                        break;
                    }
                    currentDecisions[i] = val;
                }
            }

            // --- 4. SAUVEGARDE DE SÉCURITÉ ---
            if (searchCompleted) {
                System.arraycopy(currentDecisions, 0, bestDecisions, 0, Board.NB_HOLES);
            } else {
                break;
            }
        }

        return bestDecisions;
    }

    private double minimax(AwariLong board, int depth, double alpha, double beta, boolean maximizingPlayer, int myPlayerIndex, long startTime) {
        if ((nodeCount++ & 1023) == 0) {
            if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
                timeOut = true;
                return 0.0;
            }
        }

        // Condition d'arrêt optimisée (moins de 6 graines selon les règles)
        if (depth == 0 || (48 - board.getScore(0) - board.getScore(1)) < 6) {
            return evaluate(board, myPlayerIndex);
        }

        boolean hasMove = false;
        int currentPlayer = board.getCurrentPlayer(); // À qui est-ce le tour sur ce plateau virtuel ?

        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValidMove(currentPlayer, i)) {
                    hasMove = true;

                    // ON CLONE AVANT DE SIMULER
                    AwariLong nextState = new AwariLong(board);
                    nextState.playMove(i);

                    double eval = minimax(nextState, depth - 1, alpha, beta, false, myPlayerIndex, startTime);
                    maxEval = Math.max(maxEval, eval);
                    alpha = Math.max(alpha, eval);

                    if (beta <= alpha) break;
                    if (timeOut) return maxEval;
                }
            }
            if (!hasMove) return evaluate(board, myPlayerIndex);
            return maxEval;

        } else {
            double minEval = Double.POSITIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValidMove(currentPlayer, i)) {
                    hasMove = true;

                    // ON CLONE AVANT DE SIMULER
                    AwariLong nextState = new AwariLong(board);
                    nextState.playMove(i);

                    double eval = minimax(nextState, depth - 1, alpha, beta, true, myPlayerIndex, startTime);
                    minEval = Math.min(minEval, eval);
                    beta = Math.min(beta, eval);

                    if (beta <= alpha) break;
                    if (timeOut) return minEval;
                }
            }
            if (!hasMove) return evaluate(board, myPlayerIndex);
            return minEval;
        }
    }

    private double evaluate(AwariLong board, int playerIndex) {
        int oppIndex = 1 - playerIndex;

        // TALLY_WEIGHT (x25)
        double eval = (board.getScore(playerIndex) - board.getScore(oppIndex)) * 25.0;

        for (int i = 0; i < 6; i++) {
            // Évaluation de mes trous
            int mySeeds = board.getSeeds(playerIndex, i);
            if (mySeeds > 12) eval += 28.0;
            else if (mySeeds == 0) eval -= 54.0;
            else if (mySeeds < 3) eval -= 36.0;

            // Évaluation des trous de l'adversaire
            int oppSeeds = board.getSeeds(oppIndex, i);
            if (oppSeeds > 12) eval -= 28.0;
            else if (oppSeeds == 0) eval += 54.0;
            else if (oppSeeds < 3) eval += 36.0;
        }
        return eval;
    }
}

 */