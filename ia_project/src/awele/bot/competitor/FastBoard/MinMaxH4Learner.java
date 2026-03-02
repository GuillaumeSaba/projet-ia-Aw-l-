package awele.bot.competitor.FastBoard;

import awele.bot.CompetitorBot;
import awele.core.Board;
import awele.core.InvalidBotException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/*
public class MinMaxH4Learner extends CompetitorBot {

    // --- PARAMÈTRES DE TEMPS ---
    private static final long TIME_LIMIT_MS = 90;
    private boolean timeOut = false;

    // --- PARAMÈTRES D'APPRENTISSAGE ---
    // [0]=Score, [1]=Krou, [2]=Vide, [3]=Vulnérable
    private int[] defaultWeights = {25, 28, -54, -36};
    private int[] weights = new int[4];

    private Random random;
    private List<TrainingData> dataset;

    private static class TrainingData {
        int[] myHoles;
        int[] oppHoles;
        boolean winning;
    }

    public MinMaxH4Learner() throws InvalidBotException {
        this.setBotName("MinMaxFastBoardLearner");
        this.addAuthor("SABATIER Guillaume");
        this.random = new Random();
        this.dataset = new ArrayList<>();
        System.arraycopy(defaultWeights, 0, weights, 0, 4);
    }

    @Override
    public void initialize() {
    }

    @Override
    public void finish() {
    }

    // =========================================================================
    // APPRENTISSAGE : HILL CLIMBING HYBRIDE (DATASET + SELF-PLAY)
    // =========================================================================
    @Override
    public void learn() {
        long startTime = System.currentTimeMillis();
        long maxDuration = 3600000 - 120000; // 58 minutes d'apprentissage pur

        System.err.println("Chargement de la base de données experte...");
        loadDataset("ia_project/data/awele.data");

        System.err.println("Démarrage du Hill Climbing Hybride sur FastBoard...");

        int[] currentChampion = Arrays.copyOf(defaultWeights, 4);
        int currentDatasetScore = evaluateOnDataset(currentChampion);

        // --- BENCHMARK RAPIDE ---
        int matches = 0;
        long benchStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - benchStart < 1000) {
            playMatch(currentChampion, currentChampion, 5);
            matches++;
        }
        int simDepth = (matches > 6000) ? 9 : (matches > 3000 ? 8 : 7);
        System.err.println("Benchmark FastBoard : " + matches + " matchs/sec -> Profondeur " + simDepth);

        int iteration = 0;
        int improvements = 0;

        // --- BOUCLE D'APPRENTISSAGE ---
        while (System.currentTimeMillis() - startTime < maxDuration) {

            int[] challenger = Arrays.copyOf(currentChampion, 4);

            // 1. MUTATION RESTREINTE
            int nbMutations = 1 + random.nextInt(2);
            for (int m = 0; m < nbMutations; m++) {
                int geneIdx = random.nextInt(4);
                challenger[geneIdx] += (random.nextInt(5) - 2); // Micro-mutation : -2 à +2

                // 2. BRIDAGE + ou - 10 points
                int limiteHaute = defaultWeights[geneIdx] + 10;
                int limiteBasse = defaultWeights[geneIdx] - 10;
                if (challenger[geneIdx] > limiteHaute) challenger[geneIdx] = limiteHaute;
                if (challenger[geneIdx] < limiteBasse) challenger[geneIdx] = limiteBasse;
            }

            // 3. FILTRE DU DATASET
            int challengerDatasetScore = evaluateOnDataset(challenger);
            if (challengerDatasetScore < currentDatasetScore - 1) {
                iteration++;
                continue;
            }

            // 4. VALIDATION EN SELF-PLAY
            int scoreChallenger = 0;

            int resAller = playMatch(challenger, currentChampion, simDepth);
            if (resAller > 0) scoreChallenger += 3;
            else if (resAller == 0) scoreChallenger += 1;

            int resRetour = playMatch(currentChampion, challenger, simDepth);
            if (resRetour < 0) scoreChallenger += 3;
            else if (resRetour == 0) scoreChallenger += 1;

            if (scoreChallenger > 2) {
                System.arraycopy(challenger, 0, currentChampion, 0, 4);
                currentDatasetScore = Math.max(currentDatasetScore, challengerDatasetScore);
                improvements++;
                if (improvements % 5 == 0) {
                    System.err.println("Amélioration #" + improvements + " (Dataset Score: " + currentDatasetScore + ") Poids : " + Arrays.toString(currentChampion));
                }
            }
            iteration++;
        }

        // --- 5. LE FILET DE SÉCURITÉ DE HAUTE PROFONDEUR ---
        System.err.println("Apprentissage terminé en " + iteration + " itérations.");
        System.err.println("Validation finale à haute profondeur (Depth 8)...");

        int finalScore = 0;

        int resAller = playMatch(currentChampion, defaultWeights, simDepth);
        if (resAller > 0) finalScore += 3;
        else if (resAller == 0) finalScore += 1;

        int resRetour = playMatch(defaultWeights, currentChampion, simDepth);
        if (resRetour < 0) finalScore += 3;
        else if (resRetour == 0) finalScore += 1;

        if (finalScore >= 4) {
            System.err.println("SUCCÈS : Les poids appris sont validés !");
            System.arraycopy(currentChampion, 0, this.weights, 0, 4);
        } else {
            System.err.println("PROTECTION ACTIVE : L'apprentissage est moins bon à haute profondeur.");
            System.err.println("Restauration du filet de sécurité (Poids de l'expert).");
            System.arraycopy(defaultWeights, 0, this.weights, 0, 4);
        }
    }

    // =========================================================================
    // FONCTIONS DU DATASET (Zéro création d'objet)
    // =========================================================================
    private void loadDataset(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 14) continue;
                TrainingData data = new TrainingData();
                data.myHoles = new int[6];
                data.oppHoles = new int[6];
                for (int i = 0; i < 6; i++) data.myHoles[i] = Integer.parseInt(parts[i]);
                for (int i = 0; i < 6; i++) data.oppHoles[i] = Integer.parseInt(parts[6 + i]);
                data.winning = parts[13].equals("G");
                dataset.add(data);
            }
        } catch (IOException e) {
            System.err.println("Warning: Dataset not found at " + path);
        }
    }

    private int evaluateOnDataset(int[] w) {
        int score = 0;
        for (TrainingData data : dataset) {
            double eval = evaluateDatasetRow(data.myHoles, data.oppHoles, w);
            if (data.winning && eval > 0) score += 1;
            else if (!data.winning && eval < 0) score += 1;
        }
        return score;
    }

    private double evaluateDatasetRow(int[] myHoles, int[] oppHoles, int[] w) {
        double eval = 0;
        for (int i = 0; i < 6; i++) {
            int mySeeds = myHoles[i];
            if (mySeeds > 12) eval += w[1];
            else if (mySeeds == 0) eval += w[2];
            else if (mySeeds < 3) eval += w[3];

            int oppSeeds = oppHoles[i];
            if (oppSeeds > 12) eval -= w[1];
            else if (oppSeeds == 0) eval -= w[2];
            else if (oppSeeds < 3) eval -= w[3];
        }
        return eval;
    }

    // =========================================================================
    // MOTEUR DE SIMULATION FASTBOARD (SELF PLAY)
    // =========================================================================
    private int playMatch(int[] w1, int[] w2, int depth) {
        FastBoard board = new FastBoard();
        // Simulation d'un plateau de départ
        for (int i = 0; i < 12; i++) board.holes[i] = 4;
        board.scores[0] = 0;
        board.scores[1] = 0;
        board.currentPlayer = 0;

        int moves = 0;

        while (board.getTotalSeeds() > 6 && moves < 150) {
            int cp = board.currentPlayer;
            int[] cw = (cp == 0) ? w1 : w2;

            int bestMove = -1;
            double bestVal = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    FastBoard next = board.playMove(i);
                    double val = minimaxSim(next, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, cp, cw);
                    if (val > bestVal) {
                        bestVal = val;
                        bestMove = i;
                    }
                }
            }
            if (bestMove == -1) break;
            board = board.playMove(bestMove);
            moves++;
        }

        int s1 = board.scores[0];
        int s2 = board.scores[1];
        int remaining = board.getTotalSeeds();
        if (board.currentPlayer == 0) s2 += remaining;
        else s1 += remaining;

        return Integer.compare(s1, s2);
    }

    private double minimaxSim(FastBoard board, int depth, double alpha, double beta, boolean maximizingPlayer, int myPlayerIndex, int[] w) {
        if (depth == 0 || board.getTotalSeeds() < 6) return evaluate(board, myPlayerIndex, w);

        boolean hasMove = false;
        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    hasMove = true;
                    FastBoard next = board.playMove(i);
                    double eval = minimaxSim(next, depth - 1, alpha, beta, false, myPlayerIndex, w);
                    maxEval = Math.max(maxEval, eval);
                    alpha = Math.max(alpha, eval);
                    if (beta <= alpha) break;
                }
            }
            if (!hasMove) return evaluate(board, myPlayerIndex, w);
            return maxEval;
        } else {
            double minEval = Double.POSITIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    hasMove = true;
                    FastBoard next = board.playMove(i);
                    double eval = minimaxSim(next, depth - 1, alpha, beta, true, myPlayerIndex, w);
                    minEval = Math.min(minEval, eval);
                    beta = Math.min(beta, eval);
                    if (beta <= alpha) break;
                }
            }
            if (!hasMove) return evaluate(board, myPlayerIndex, w);
            return minEval;
        }
    }

    // =========================================================================
    // FONCTION D'ÉVALUATION
    // =========================================================================
    private double evaluate(FastBoard board, int playerIndex, int[] w) {
        int oppIndex = 1 - playerIndex;
        double eval = (board.scores[playerIndex] - board.scores[oppIndex]) * w[0];

        for (int i = 0; i < 6; i++) {
            // Lecture directe dans les tableaux int de FastBoard
            int mySeeds = board.holes[playerIndex * 6 + i];
            if (mySeeds > 12) eval += w[1];
            else if (mySeeds == 0) eval += w[2];
            else if (mySeeds < 3) eval += w[3];

            int oppSeeds = board.holes[oppIndex * 6 + i];
            if (oppSeeds > 12) eval -= w[1];
            else if (oppSeeds == 0) eval -= w[2];
            else if (oppSeeds < 3) eval -= w[3];
        }
        return eval;
    }

    // =========================================================================
    // LOGIQUE DU BOT EN COMPÉTITION
    // =========================================================================
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

        // Vérification condition d'arrêt avec votre optimisation mathématique (0 appel de méthode)
        if (depth == 0 || (48 - board.scores[0] - board.scores[1]) < 2) {
            return evaluate(board, myPlayerIndex, this.weights);
        }

        boolean hasMove = false;

        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    hasMove = true; // On a trouvé un coup, on lève le drapeau

                    FastBoard nextState = board.playMove(i);
                    double eval = minimax(nextState, depth - 1, alpha, beta, false, myPlayerIndex, startTime);
                    maxEval = Math.max(maxEval, eval);
                    alpha = Math.max(alpha, eval);

                    if (beta <= alpha) break; // Élagage Alpha-Beta
                    if (timeOut) return maxEval; // Sécurité pour remonter vite en cas de Timeout
                }
            }

            // Gérer le cas de famine : si on a fait le tour des 6 trous et qu'aucun n'était valide
            if (!hasMove) {
                return evaluate(board, myPlayerIndex, this.weights);
            }

            return maxEval;

        } else {
            double minEval = Double.POSITIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    hasMove = true; // L'adversaire a au moins un coup

                    FastBoard nextState = board.playMove(i);
                    double eval = minimax(nextState, depth - 1, alpha, beta, true, myPlayerIndex, startTime);
                    minEval = Math.min(minEval, eval);
                    beta = Math.min(beta, eval);

                    if (beta <= alpha) break; // Élagage Alpha-Beta
                    if (timeOut) return minEval; // Sécurité Timeout
                }
            }

            // Gérer le cas de famine pour l'adversaire
            if (!hasMove) {
                return evaluate(board, myPlayerIndex, this.weights);
            }

            return minEval;
        }
    }

    // =========================================================================
    // FASTBOARD OPTIMISÉ (AVEC CONSTRUCTEUR DE COPIE)
    // =========================================================================
    private class FastBoard {
        int[] holes = new int[12];
        int[] scores = new int[2];
        int currentPlayer;

        // Constructeur privé pour initialisation manuelle dans playMatch
        private FastBoard() {
        }

        // Constructeur officiel depuis le Board principal
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

        // Constructeur de COPIE (ZÉRO ALLOCATION DE TABLEAU SUPPLÉMENTAIRE)
        private FastBoard(FastBoard source) {
            System.arraycopy(source.holes, 0, this.holes, 0, 12);
            this.scores[0] = source.scores[0];
            this.scores[1] = source.scores[1];
            this.currentPlayer = source.currentPlayer;
        }

        public int getTotalSeeds() {
            return 48 - this.scores[0] - this.scores[1];
        }

        public boolean isValid(int moveIndex) {
            int pos = this.currentPlayer * 6 + moveIndex;
            if (this.holes[pos] == 0) return false;
            int oppSide = 1 - this.currentPlayer;
            boolean opponentEmpty = true;
            for (int i = 0; i < 6; i++) {
                if (this.holes[oppSide * 6 + i] > 0) {
                    opponentEmpty = false;
                    break;
                }
            }
            if (opponentEmpty) return (moveIndex + this.holes[pos] >= 6);
            return true;
        }

        public FastBoard playMove(int moveIndex) {
            // Utilisation du constructeur de copie
            FastBoard next = new FastBoard(this);

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