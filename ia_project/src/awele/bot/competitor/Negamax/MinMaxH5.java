package awele.bot.competitor.Negamax;
import awele.core.Board;
import awele.bot.CompetitorBot;
import awele.core.InvalidBotException;

import java.util.Arrays;
/*
public class MinMaxH5 extends CompetitorBot {

    // --- PARAMÈTRES DE TEMPS ---
    private static final long TIME_LIMIT_MS = 90;
    private boolean timeOut = false;

    public MinMaxH5() throws InvalidBotException {
        this.setBotName("NegaMax");
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

        // --- 1. L'OUVERTURE FORCÉE ---
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

        // On crée notre plateau ultra-léger pour les simulations
        FastBoard fastRoot = new FastBoard(board);

        // --- 3. APPROFONDISSEMENT ITÉRATIF ---
        for (int depth = 1; depth <= 50; depth++) {
            double[] currentDecisions = new double[Board.NB_HOLES];
            Arrays.fill(currentDecisions, Double.NEGATIVE_INFINITY);
            boolean searchCompleted = true;

            // --- TRI PRIMITIF (Zéro allocation d'objet) ---
            int[] moveOrder = {0, 1, 2, 3, 4, 5};
            if (depth > 1) {
                // Tri à bulles ultra-rapide sur 6 éléments
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

            double alpha = Double.NEGATIVE_INFINITY;
            double beta = Double.POSITIVE_INFINITY;

            // --- SIMULATION ---
            for (int holeIndex = 0; holeIndex < Board.NB_HOLES; holeIndex++) {
                int i = moveOrder[holeIndex];

                if (fastRoot.isValid(i)) {
                    FastBoard nextState = fastRoot.playMove(i);
                    double val = -negamax(nextState, depth - 1, -beta, -alpha, startTime);

                    if (timeOut) {
                        searchCompleted = false;
                        break;
                    }
                    currentDecisions[i] = val;

                    alpha = Math.max(alpha, val);
                }
            }

            // --- 4. SAUVEGARDE DE SÉCURITÉ ---
            if (searchCompleted) {
                System.arraycopy(currentDecisions, 0, bestDecisions, 0, Board.NB_HOLES);
            } else {
                break; // Le temps est écoulé
            }
        }

        return bestDecisions;
    }

    private double negamax(FastBoard board, int depth, double alpha, double beta, long startTime) {
        if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
            this.timeOut = true;
            return 0.0;
        }

        if (depth == 0 || (48 - board.scores[0] - board.scores[1]) < 2) {
            return evaluate(board, board.currentPlayer);
        }

        boolean hasMove = false;
        double maxEval = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < 6; i++) {
            if (board.isValid(i)) {
                hasMove = true;
                FastBoard nextState = board.playMove(i);

                // Le signe '-', et on croise et inverse beta et alpha
                double eval = -negamax(nextState, depth - 1, -beta, -alpha, startTime);

                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);

                if (alpha >= beta) break; // Élagage
                if (timeOut) return maxEval;
            }
        }

        if (!hasMove) {
            return evaluate(board, board.currentPlayer);
        }

        return maxEval;
    }

    private double evaluate(FastBoard board, int playerIndex) {
        int oppIndex = 1 - playerIndex;

        // 1. TALLY_WEIGHT (x25)
        double eval = (board.scores[playerIndex] - board.scores[oppIndex]) * 25.0;

        // 2. Évaluation positionnelle de Joan Sala
        for (int i = 0; i < 6; i++) {
            // Pour les trous du joueur courant
            int mySeeds = board.holes[playerIndex * 6 + i];
            if (mySeeds > 12) eval += 28.0;
            else if (mySeeds == 0) eval -= 54.0;
            else if (mySeeds < 3) eval -= 36.0;

            // Pour les trous de l'adversaire (L'inverse)
            int oppSeeds = board.holes[oppIndex * 6 + i];
            if (oppSeeds > 12) eval -= 28.0;
            else if (oppSeeds == 0) eval += 54.0;
            else if (oppSeeds < 3) eval += 36.0;
        }
        return eval;
    }

    // =========================================================================
    // CLASSE INTERNE : FastBoard (Moteur de simulation ultra-rapide)
    // Ne génère AUCUN objet (zéro Garbage Collection) pendant les simulations.
    // =========================================================================
    private class FastBoard {
        int[] holes = new int[12]; // Index 0-5 (J0), Index 6-11 (J1)
        int[] scores = new int[2];
        int currentPlayer;

        private FastBoard() {
        }

        // Constructeur qui traduit le vrai Board officiel au tout début
        public FastBoard(Board b) {
            this.currentPlayer = b.getCurrentPlayer();
            this.scores[0] = b.getScore(0);
            this.scores[1] = b.getScore(1);

            int[] p0 = (this.currentPlayer == 0) ? b.getPlayerHoles() : b.getOpponentHoles();
            int[] p1 = (this.currentPlayer == 1) ? b.getPlayerHoles() : b.getOpponentHoles();

            for (int i = 0; i < 6; i++) {
                this.holes[i] = p0[i];
                this.holes[6 + i] = p1[i];
            }
        }

        public int getTotalSeeds() {
            return 48 - this.scores[0] - this.scores[1];
        }

        public boolean isValid(int moveIndex) {
            int pos = this.currentPlayer * 6 + moveIndex;
            if (this.holes[pos] == 0) return false;

            // Vérification famine
            int oppSide = 1 - this.currentPlayer;
            boolean opponentEmpty = true;
            for (int i = 0; i < 6; i++) {
                if (this.holes[oppSide * 6 + i] > 0) {
                    opponentEmpty = false;
                    break;
                }
            }
            if (opponentEmpty) {
                return (moveIndex + this.holes[pos] >= 6);
            }
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
                if (currentHole == pos) continue; // On ne nourrit pas le trou de départ
                next.holes[currentHole]++;
                seeds--;
            }

            int oppSide = 1 - side;
            // Règle de capture : on est dans le camp adverse et la case contient 2 ou 3 graines
            if (currentHole / 6 == oppSide && (next.holes[currentHole] == 2 || next.holes[currentHole] == 3)) {

                // Vérifier si cette capture affamerait totalement l'adversaire (Take All rule)
                boolean takeAll = true;
                for (int i = 0; i <= currentHole % 6; i++) {
                    int val = next.holes[oppSide * 6 + i];
                    if (val == 1 || val > 3) {
                        takeAll = false;
                        break;
                    }
                }
                if (takeAll) {
                    for (int i = (currentHole % 6) + 1; i < 6; i++) {
                        if (next.holes[oppSide * 6 + i] != 0) {
                            takeAll = false;
                            break;
                        }
                    }
                }

                // On capture en remontant (sens inverse)
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

 */