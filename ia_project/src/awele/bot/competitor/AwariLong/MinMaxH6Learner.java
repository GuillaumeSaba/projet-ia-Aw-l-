package awele.bot.competitor.AwariLong;

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

public class MinMaxH6Learner extends CompetitorBot {

    // --- PARAMÈTRES DE TEMPS ---
    private static final long TIME_LIMIT_MS = 95;
    private boolean timeOut = false;

    // --- PARAMÈTRES D'APPRENTISSAGE ---
    // [0]=Score, [1]=Krou, [2]=Vide, [3]=Vulnérable
    private int[] defaultWeights = {25, 28, -54, -36};
    private int[] weights = new int[4]; //Tableau pour l'apprentissage
    private int w0, w1, w2, w3; //variable pour eviter de parcourir plusieurs fois le tableau

    private Random random;
    private List<TrainingData> dataset;

    private static class TrainingData {
        int[] myHoles;
        int[] oppHoles;
        boolean winning;
    }

    public MinMaxH6Learner() throws InvalidBotException {
        this.setBotName("MinMaxAwariLearner");
        this.addAuthor("Iliass FERCHACH");
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
    // APPRENTISSAGE : HILL CLIMBING HYBRIDE
    // =========================================================================
    @Override
    public void learn() {
        long startTime = System.currentTimeMillis();
        long maxDuration = 3600000 - 120000; // 58 minutes d'apprentissage pur (Marge de 2 min)

        System.err.println("Chargement de la base de données experte...");
        loadDataset("ia_project/data/awele.data");

        System.err.println("Démarrage du Hill Climbing Hybride avec Régularisation...");

        int[] currentChampion = Arrays.copyOf(defaultWeights, 4);
        int currentDatasetScore = evaluateOnDataset(currentChampion);

        // --- BENCHMARK RAPIDE ---
        int matches = 0;
        long benchStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - benchStart < 1000) {
            // CORRECTION : Ajout de Long.MAX_VALUE
            playMatch(currentChampion, currentChampion, 5, Long.MAX_VALUE);
            matches++;
        }
        int simDepth = (matches > 6000) ? 9 : (matches > 3000 ? 8 : 7);
        System.err.println("Benchmark : " + matches + " matchs/sec -> Profondeur d'entraînement " + simDepth);

        int iteration = 0;
        int improvements = 0;

        // --- BOUCLE D'APPRENTISSAGE ---
        while (System.currentTimeMillis() - startTime < maxDuration) {

            int[] challenger = Arrays.copyOf(currentChampion, 4);

            // 1. MUTATION RESTREINTE (RÉGULARISATION)
            int nbMutations = 1 + random.nextInt(2);
            for (int m = 0; m < nbMutations; m++) {
                int geneIdx = random.nextInt(4);
                challenger[geneIdx] += (random.nextInt(5) - 2); // Micro-mutation : -2 à +2

                // 2. BRIDAGE (CLAMPING) +/- 10 points
                int limiteHaute = defaultWeights[geneIdx] + 10;
                int limiteBasse = defaultWeights[geneIdx] - 10;
                if (challenger[geneIdx] > limiteHaute) challenger[geneIdx] = limiteHaute;
                if (challenger[geneIdx] < limiteBasse) challenger[geneIdx] = limiteBasse;
            }

            // 3. FILTRE DU DATASET (EXTRACTION DE CONNAISSANCES)
            int challengerDatasetScore = evaluateOnDataset(challenger);
            if (challengerDatasetScore < currentDatasetScore - 1) {
                iteration++;
                continue; // Rejet instantané si le dataset n'est pas convaincu
            }

            // 4. VALIDATION EN SELF-PLAY
            int scoreChallenger = 0;

            // CORRECTION : Utilisation de Long.MAX_VALUE au lieu de deadlineAller
            int resAller = playMatch(challenger, currentChampion, simDepth, Long.MAX_VALUE);
            if (resAller > 0) scoreChallenger += 3;
            else if (resAller == 0) scoreChallenger += 1;

            // CORRECTION : Ajout de Long.MAX_VALUE
            int resRetour = playMatch(currentChampion, challenger, simDepth, Long.MAX_VALUE);
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

        /*
        // --- 5. LE FILET DE SÉCURITÉ DE HAUTE PROFONDEUR ---
        System.err.println("Apprentissage terminé en " + iteration + " itérations.");
        System.err.println("Validation finale à haute profondeur (Depth 8)...");

        int finalScore = 0;
        int validationDepth = 8;

        // On donne 10 secondes MAX par match pour ne jamais dépasser l'heure
        long deadlineAller = System.currentTimeMillis() + 10000;
        int resAller = playMatch(currentChampion, defaultWeights, validationDepth, deadlineAller);
        if (resAller > 0) finalScore += 3; else if (resAller == 0) finalScore += 1;

        long deadlineRetour = System.currentTimeMillis() + 10000;
        int resRetour = playMatch(defaultWeights, currentChampion, validationDepth, deadlineRetour);
        if (resRetour < 0) finalScore += 3; else if (resRetour == 0) finalScore += 1;

        if (finalScore >= 4) {
            System.err.println("SUCCÈS : Les poids appris sont validés !");
            System.arraycopy(currentChampion, 0, this.weights, 0, 4);
        } else {
            System.err.println("PROTECTION ACTIVE : Match annulé (Trop long) ou Apprentissage moins bon.");
            System.err.println("Restauration du filet de sécurité (Poids de l'expert).");
            System.arraycopy(defaultWeights, 0, this.weights, 0, 4);
        }

         */

        System.arraycopy(currentChampion, 0, this.weights, 0, 4);

        // --- 6. INITIALISATION DES REGISTRES CPU ---
        this.w0 = this.weights[0];
        this.w1 = this.weights[1];
        this.w2 = this.weights[2];
        this.w3 = this.weights[3];

        //je clear la RAM
        this.dataset.clear();
        this.dataset = null;
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
        double eval = 0; // Pas de score au début de partie dans les datasets
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
    // MOTEUR DE SIMULATION (SELF PLAY)
    // =========================================================================
    private int playMatch(int[] w1, int[] w2, int depth, long deadline) {
        AwariLong board = new AwariLong();
        int moves = 0;

        while ((48 - board.getScore(0) - board.getScore(1)) > 6 && moves < 150) {
            // STOP D'URGENCE SI LE TEMPS EST DÉPASSÉ
            if (System.currentTimeMillis() > deadline) return 0; // Match nul forcé

            int cp = board.getCurrentPlayer();
            int[] cw = (cp == 0) ? w1 : w2;
            int w0 = cw[0], w_1 = cw[1], w_2 = cw[2], w_3 = cw[3]; // Extraction pour la vitesse

            int bestMove = -1;
            double bestVal = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < 6; i++) {
                if (board.isValidMove(cp, i)) {
                    AwariLong next = new AwariLong(board);
                    next.playMove(i);
                    double val = minimaxSim(next, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, cp, cw);
                    if (val > bestVal) { bestVal = val; bestMove = i; }
                }
            }
            if (bestMove == -1) break;
            board.playMove(bestMove);
            moves++;
        }

        int s1 = board.getScore(0);
        int s2 = board.getScore(1);
        int remaining = 48 - s1 - s2;
        if (board.getCurrentPlayer() == 0) s2 += remaining; else s1 += remaining;

        return Integer.compare(s1, s2);
    }

    private double minimaxSim(AwariLong board, int depth, double alpha, double beta, boolean maximizingPlayer, int myPlayerIndex, int[] w) {
        if (depth == 0 || (48 - board.getScore(0) - board.getScore(1)) < 6) return evaluate(board, myPlayerIndex);

        boolean hasMove = false;
        int cp = board.getCurrentPlayer();

        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValidMove(cp, i)) {
                    hasMove = true;
                    AwariLong next = new AwariLong(board);
                    next.playMove(i);
                    double eval = minimaxSim(next, depth - 1, alpha, beta, false, myPlayerIndex, w);
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
                if (board.isValidMove(cp, i)) {
                    hasMove = true;
                    AwariLong next = new AwariLong(board);
                    next.playMove(i);
                    double eval = minimaxSim(next, depth - 1, alpha, beta, true, myPlayerIndex, w);
                    minEval = Math.min(minEval, eval);
                    beta = Math.min(beta, eval);
                    if (beta <= alpha) break;
                }
            }
            if (!hasMove) return evaluate(board, myPlayerIndex);
            return minEval;
        }
    }

    // =========================================================================
    // FONCTION D'ÉVALUATION
    // =========================================================================
    private double evaluate(AwariLong board, int playerIndex) {
        int oppIndex = 1 - playerIndex;
        double eval = (board.getScore(playerIndex) - board.getScore(oppIndex)) * this.w0;

        for (int i = 0; i < 6; i++) {
            int mySeeds = board.getSeeds(playerIndex, i);
            if (mySeeds > 12) eval += this.w1;
            else if (mySeeds == 0) eval += this.w2;
            else if (mySeeds < 3) eval += this.w3;

            int oppSeeds = board.getSeeds(oppIndex, i);
            if (oppSeeds > 12) eval -= this.w1;
            else if (oppSeeds == 0) eval -= this.w2;
            else if (oppSeeds < 3) eval -= this.w3;
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

        // On crée notre plateau léger basé sur les bits
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
        if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
            timeOut = true;
            return 0.0;
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
}
