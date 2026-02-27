package awele.bot.competitor.FastBoard;

import awele.bot.CompetitorBot;
import awele.core.Board;
import awele.core.InvalidBotException;

import java.util.Arrays;

/**
 * MinMaxH4Learner : Version "Turbo" de MinMaxFastBoard.
 * - Pas d'History Heuristic (trop lent).
 * - Limite de temps stricte (85ms).
 * - Heuristique légèrement plus agressive (pénalité trou vide réduite).
 */
public class MinMaxH4Learner extends CompetitorBot {

    private static final long TIME_LIMIT_MS = 85; // Marge de sécurité maximale
    private boolean timeOut = false;

    public MinMaxH4Learner() throws InvalidBotException {
        this.setBotName("MinMaxH4Learner");
        this.addAuthor("SABATIER Guillaume");
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

        // Ouverture Forcée (Trou 5)
        if (board.getLog(currentPlayer).length == 0 && board.getLog(opponentPlayer).length == 0) {
            double[] firstMoveDecision = new double[Board.NB_HOLES];
            Arrays.fill(firstMoveDecision, Double.NEGATIVE_INFINITY);
            if (board.validMoves(currentPlayer)[5]) {
                firstMoveDecision[5] = 1.0;
                return firstMoveDecision;
            }
        }

        double[] bestDecisions = new double[Board.NB_HOLES];
        Arrays.fill(bestDecisions, Double.NEGATIVE_INFINITY);

        long startTime = System.currentTimeMillis();
        this.timeOut = false;

        FastBoard fastRoot = new FastBoard(board);

        for (int depth = 1; depth <= 60; depth++) {
            double[] currentDecisions = new double[Board.NB_HOLES];
            Arrays.fill(currentDecisions, Double.NEGATIVE_INFINITY);
            boolean searchCompleted = true;

            // Tri basique : on utilise les scores de l'itération précédente
            Integer[] rootMoves = {0, 1, 2, 3, 4, 5};
            if (depth > 1) {
                final double[] prevDecisions = bestDecisions;
                Arrays.sort(rootMoves, (a, b) -> Double.compare(prevDecisions[b], prevDecisions[a]));
            }

            for (int i : rootMoves) {
                if (fastRoot.isValid(i)) {
                    FastBoard nextState = fastRoot.playMove(i);
                    double val = minimax(nextState, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, currentPlayer, startTime);

                    if (timeOut) {
                        searchCompleted = false;
                        break;
                    }
                    currentDecisions[i] = val;
                }
            }

            if (searchCompleted) {
                System.arraycopy(currentDecisions, 0, bestDecisions, 0, Board.NB_HOLES);
            } else {
                break;
            }
        }

        return bestDecisions;
    }

    private double minimax(FastBoard board, int depth, double alpha, double beta, boolean maximizingPlayer, int myPlayerIndex, long startTime) {
        if ((nodes++ & 511) == 0) { // Check moins fréquent (512) pour gagner du temps
            if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
                timeOut = true;
                return 0.0;
            }
        }

        if (depth == 0 || board.getTotalSeeds() < 2) {
            return evaluate(board, myPlayerIndex);
        }

        boolean hasMove = false;
        
        // Ordre naturel 0..5 (le plus rapide sans tri)
        // MinMaxFastBoard utilise un tri à bulles à la racine mais pas dans l'arbre.
        // Ici on ne trie pas dans l'arbre pour maximiser la vitesse.

        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    hasMove = true;
                    FastBoard nextState = board.playMove(i);
                    double eval = minimax(nextState, depth - 1, alpha, beta, false, myPlayerIndex, startTime);
                    maxEval = Math.max(maxEval, eval);
                    alpha = Math.max(alpha, eval);

                    if (beta <= alpha) break;
                }
            }
            if (!hasMove) return evaluate(board, myPlayerIndex);
            return maxEval;

        } else {
            double minEval = Double.POSITIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    hasMove = true;
                    FastBoard nextState = board.playMove(i);
                    double eval = minimax(nextState, depth - 1, alpha, beta, true, myPlayerIndex, startTime);
                    minEval = Math.min(minEval, eval);
                    beta = Math.min(beta, eval);

                    if (beta <= alpha) break;
                }
            }
            if (!hasMove) return evaluate(board, myPlayerIndex);
            return minEval;
        }
    }
    
    private long nodes = 0;

    private double evaluate(FastBoard board, int myPlayerIndex) {
        int oppIndex = 1 - myPlayerIndex;
        double eval = (board.scores[myPlayerIndex] - board.scores[oppIndex]) * 25.0;

        for (int i = 0; i < 6; i++) {
            int mySeeds = board.holes[myPlayerIndex * 6 + i];
            int oppSeeds = board.holes[oppIndex * 6 + i];

            if (mySeeds > 12) eval += 28.0;
            else if (mySeeds == 0) eval -= 50.0; // Pénalité réduite (-54 -> -50)
            else if (mySeeds < 3) eval -= 36.0;

            if (oppSeeds > 12) eval -= 28.0;
            else if (oppSeeds == 0) eval += 50.0; // Bonus réduit (+54 -> +50)
            else if (oppSeeds < 3) eval += 36.0;
        }
        return eval;
    }

    private class FastBoard {
        int[] holes = new int[12];
        int[] scores = new int[2];
        int currentPlayer;
        private FastBoard() {}
        public FastBoard(Board b) {
            this.currentPlayer = b.getCurrentPlayer();
            this.scores[0] = b.getScore(0);
            this.scores[1] = b.getScore(1);
            int[] p0 = (this.currentPlayer == 0) ? b.getPlayerHoles() : b.getOpponentHoles();
            int[] p1 = (this.currentPlayer == 1) ? b.getPlayerHoles() : b.getOpponentHoles();
            for (int i = 0; i < 6; i++) { this.holes[i] = p0[i]; this.holes[6 + i] = p1[i]; }
        }
        public int getTotalSeeds() { return 48 - this.scores[0] - this.scores[1]; }
        public boolean isValid(int moveIndex) {
            int pos = this.currentPlayer * 6 + moveIndex;
            if (this.holes[pos] == 0) return false;
            int oppSide = 1 - this.currentPlayer;
            boolean opponentEmpty = true;
            for (int i = 0; i < 6; i++) if (this.holes[oppSide * 6 + i] > 0) { opponentEmpty = false; break; }
            if (opponentEmpty) return (moveIndex + this.holes[pos] >= 6);
            return true;
        }
        public FastBoard playMove(int moveIndex) {
            FastBoard next = new FastBoard();
            System.arraycopy(this.holes, 0, next.holes, 0, 12);
            next.scores[0] = this.scores[0];
            next.scores[1] = this.scores[1];
            next.currentPlayer = this.currentPlayer;
            int side = next.currentPlayer;
            int pos = side * 6 + moveIndex;
            int seeds = next.holes[pos];
            next.holes[pos] = 0;
            int currentHole = pos;
            while (seeds > 0) {
                currentHole = (currentHole + 1) % 12;
                if (currentHole == pos) continue;
                next.holes[currentHole]++;
                seeds--;
            }
            int oppSide = 1 - side;
            if (currentHole / 6 == oppSide && (next.holes[currentHole] == 2 || next.holes[currentHole] == 3)) {
                boolean takeAll = true;
                for (int i = 0; i <= currentHole % 6; i++) {
                    int val = next.holes[oppSide * 6 + i];
                    if (val == 1 || val > 3) { takeAll = false; break; }
                }
                if (takeAll) {
                    for (int i = (currentHole % 6) + 1; i < 6; i++) {
                        if (next.holes[oppSide * 6 + i] != 0) { takeAll = false; break; }
                    }
                }
                if (!takeAll) {
                    while (currentHole >= 0 && currentHole / 6 == oppSide && (next.holes[currentHole] == 2 || next.holes[currentHole] == 3)) {
                        next.scores[side] += next.holes[currentHole];
                        next.holes[currentHole] = 0;
                        currentHole--;
                    }
                }
            }
            next.currentPlayer = 1 - side;
            return next;
        }
    }
}