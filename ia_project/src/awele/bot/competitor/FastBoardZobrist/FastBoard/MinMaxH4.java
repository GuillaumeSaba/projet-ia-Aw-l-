package awele.bot.competitor.FastBoardZobrist.FastBoard;

import awele.bot.CompetitorBot;
import awele.core.Board;
import awele.core.InvalidBotException;

import java.util.Arrays;
/*
public class MinMaxH4 extends CompetitorBot {

    // --- PARAMÈTRES DE TEMPS ---
    private static final long TIME_LIMIT_MS = 90;
    private boolean timeOut = false;

    // --- VARIABLES ZOBRIST ET TABLE DE TRANSPOSITION ---
    private static final long[][] ZOBRIST = new long[12][49]; // 12 trous, jusqu'à 48 graines
    private static final long ZOBRIST_PLAYER;

    // Table de 1 million d'entrées (2^20) pour respecter les 64Mo de RAM
    private static final int TT_SIZE = 1 << 20;
    private static final int TT_MASK = TT_SIZE - 1;

    // Tableaux primitifs (Pas de création d'objets = Zéro Garbage Collector !)
    private long[] ttKeys = new long[TT_SIZE];
    private double[] ttValues = new double[TT_SIZE];
    private int[] ttDepths = new int[TT_SIZE];
    private byte[] ttFlags = new byte[TT_SIZE];

    // Flags pour l'Alpha-Bêta
    private static final byte TT_EXACT = 1;
    private static final byte TT_LOWER = 2; // Coupure Bêta (Fail high)
    private static final byte TT_UPPER = 3; // Coupure Alpha (Fail low)

    // Bloc statique générant les clés Zobrist une seule fois au chargement
    static {
        java.util.Random rnd = new java.util.Random(123456789L);
        for (int i = 0; i < 12; i++) {
            for (int s = 0; s <= 48; s++) {
                ZOBRIST[i][s] = rnd.nextLong();
            }
        }
        ZOBRIST_PLAYER = rnd.nextLong();
    }

    public MinMaxH4() throws InvalidBotException {
        this.setBotName("MinMaxFastBoardZobrist");
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

            // --- SIMULATION ---
            for (int holeIndex = 0; holeIndex < Board.NB_HOLES; holeIndex++) {
                int i = moveOrder[holeIndex];

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

            // --- 4. SAUVEGARDE DE SÉCURITÉ ---
            if (searchCompleted) {
                System.arraycopy(currentDecisions, 0, bestDecisions, 0, Board.NB_HOLES);
            } else {
                break; // Le temps est écoulé
            }
        }

        return bestDecisions;
    }

    private double minimax(FastBoard board, int depth, double alpha, double beta, boolean maximizingPlayer, int myPlayerIndex, long startTime) {
        // Alerte Chronomètre
        if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
            timeOut = true;
            return 0.0;
        }

        // Vérification condition d'arrêt
        if (depth == 0 || (48 - board.scores[0] - board.scores[1]) < 6) { // Correction de < 2 à < 6 selon le PDF
            return evaluate(board, myPlayerIndex);
        }

        // --- 1. LECTURE DE LA TABLE (TT PROBE) ---
        long hash = getHash(board);
        int ttIndex = (int) (hash & TT_MASK);

        if (ttKeys[ttIndex] == hash && ttDepths[ttIndex] >= depth) {
            byte flag = ttFlags[ttIndex];
            double ttVal = ttValues[ttIndex];

            if (flag == TT_EXACT) return ttVal;
            if (flag == TT_LOWER) alpha = Math.max(alpha, ttVal);
            if (flag == TT_UPPER) beta = Math.min(beta, ttVal);

            if (alpha >= beta) return ttVal;
        }

        double alphaOrig = alpha;
        double betaOrig = beta;
        boolean hasMove = false;

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
                    if (timeOut) return maxEval;
                }
            }
            if (!hasMove) return evaluate(board, myPlayerIndex);

            // --- 2. SAUVEGARDE DANS LA TABLE (TT STORE) BLOC MAX ---
            ttKeys[ttIndex] = hash;
            ttValues[ttIndex] = maxEval;
            ttDepths[ttIndex] = depth;
            if (maxEval <= alphaOrig) ttFlags[ttIndex] = TT_UPPER;
            else if (maxEval >= betaOrig) ttFlags[ttIndex] = TT_LOWER;
            else ttFlags[ttIndex] = TT_EXACT;

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
                    if (timeOut) return minEval;
                }
            }
            if (!hasMove) return evaluate(board, myPlayerIndex);

            // --- 2. SAUVEGARDE DANS LA TABLE (TT STORE) BLOC MIN ---
            ttKeys[ttIndex] = hash;
            ttValues[ttIndex] = minEval;
            ttDepths[ttIndex] = depth;
            if (minEval <= alphaOrig) ttFlags[ttIndex] = TT_UPPER;
            else if (minEval >= betaOrig) ttFlags[ttIndex] = TT_LOWER;
            else ttFlags[ttIndex] = TT_EXACT;

            return minEval;
        }
    }

    private double evaluate(FastBoard board, int myPlayerIndex) {
        int oppIndex = 1 - myPlayerIndex;
        double eval = (board.scores[myPlayerIndex] - board.scores[oppIndex]) * 25.0;

        for (int i = 0; i < 6; i++) {
            int mySeeds = board.holes[myPlayerIndex * 6 + i];
            int oppSeeds = board.holes[oppIndex * 6 + i];

            // Heuristique pour MES trous
            if (mySeeds > 12) eval += 28.0;
            else if (mySeeds == 0) eval -= 54.0;
            else if (mySeeds < 3) eval -= 36.0;

            // Heuristique pour les trous de L'ADVERSAIRE
            if (oppSeeds > 12) eval -= 28.0;
            else if (oppSeeds == 0) eval += 54.0;
            else if (oppSeeds < 3) eval += 36.0;
        }
        return eval;
    }

    private long getHash(FastBoard board) {
        long hash = 0;

        // 1. On intègre le joueur dont c'est le tour (assurez-vous du nom de votre variable)
        if (board.currentPlayer == 1) {
            hash ^= ZOBRIST_PLAYER;
        }

        // 2. On hache directement les 12 trous en une seule boucle
        for (int i = 0; i < 12; i++) {
            int seeds = board.holes[i];
            // Sécurité : au cas où un trou contiendrait plus de 48 graines (impossible en théorie)
            if (seeds > 48) seeds = 48;

            hash ^= ZOBRIST[i][seeds];
        }

        return hash;
    }

    // =========================================================================
    // CLASSE INTERNE : FastBoard (Moteur de simulation ultra-rapide)
    // Ne génère AUCUN objet (zéro Garbage Collection) pendant les simulations.
    // =========================================================================
    private class FastBoard {
        int[] holes = new int[12]; // Index 0-5 (J0), Index 6-11 (J1)
        int[] scores = new int[2];
        int currentPlayer;

        // Constructeur de copie ultra-rapide (Remplace le constructeur vide)
        private FastBoard(FastBoard source) {
            System.arraycopy(source.holes, 0, this.holes, 0, 12);
            this.scores[0] = source.scores[0];
            this.scores[1] = source.scores[1];
            this.currentPlayer = source.currentPlayer;
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
            FastBoard next = new FastBoard(this);

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

