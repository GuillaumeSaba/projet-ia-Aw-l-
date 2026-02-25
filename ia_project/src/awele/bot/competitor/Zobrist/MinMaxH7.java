package awele.bot.competitor.Zobrist;

import awele.bot.CompetitorBot;

/*
public class MinMaxH7 extends CompetitorBot {

    // --- PARAMÈTRES DE TEMPS ---
    private static final long TIME_LIMIT_MS = 90;
    private boolean timeOut = false;
    private int nodeCount = 0;

    // --- VARIABLES ZOBRIST ET TABLE DE TRANSPOSITION ---
    private static final long[][] ZOBRIST = new long[12][49]; // 12 trous, jusqu'à 48 graines max
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

    public MinMaxH7() throws InvalidBotException {
        this.setBotName("MinMaxZobrist");
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

// --- 1. LECTURE DE LA TABLE (TT PROBE) ---
        long hash = getHash(board);
        int ttIndex = (int) (hash & TT_MASK); // Trouve la case dans le tableau

        // Si la position est connue et que l'ancienne recherche était au moins aussi profonde
        if (ttKeys[ttIndex] == hash && ttDepths[ttIndex] >= depth) {
            byte flag = ttFlags[ttIndex];
            double ttVal = ttValues[ttIndex];

            if (flag == TT_EXACT) return ttVal;
            if (flag == TT_LOWER) alpha = Math.max(alpha, ttVal);
            if (flag == TT_UPPER) beta = Math.min(beta, ttVal);

            if (alpha >= beta) return ttVal; // La table provoque une coupure instantanée !
        }

        // On sauvegarde les bornes originales pour la sauvegarde TT à la fin
        double alphaOrig = alpha;
        double betaOrig = beta;

        boolean hasMove = false;
        int currentPlayer = board.getCurrentPlayer();

        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValidMove(currentPlayer, i)) {
                    hasMove = true;
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

            // --- 2. SAUVEGARDE DANS LA TABLE (TT STORE) ---
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
                if (board.isValidMove(currentPlayer, i)) {
                    hasMove = true;
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

            // --- 2. SAUVEGARDE DANS LA TABLE (TT STORE) ---
            ttKeys[ttIndex] = hash;
            ttValues[ttIndex] = minEval;
            ttDepths[ttIndex] = depth;
            if (minEval >= betaOrig) ttFlags[ttIndex] = TT_LOWER;
            else if (minEval <= alphaOrig) ttFlags[ttIndex] = TT_UPPER;
            else ttFlags[ttIndex] = TT_EXACT;

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

    private long getHash(AwariLong board) {
        long hash = 0;
        // On intègre le joueur dont c'est le tour dans le hachage
        if (board.getCurrentPlayer() == 1) {
            hash ^= ZOBRIST_PLAYER;
        }
        // On hache les 6 trous du joueur 0 et les 6 trous du joueur 1
        for (int i = 0; i < 6; i++) {
            hash ^= ZOBRIST[i][board.getSeeds(0, i)];
            hash ^= ZOBRIST[i + 6][board.getSeeds(1, i)];
        }
        return hash;
    }
}
*/