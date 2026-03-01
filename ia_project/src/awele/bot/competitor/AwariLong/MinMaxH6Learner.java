package awele.bot.competitor.AwariLong;

import awele.bot.CompetitorBot;
import awele.core.Board;
import awele.core.InvalidBotException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class MinMaxH6Learner extends CompetitorBot {

    // --- PARAMÈTRES DE TEMPS ---
    private static final long TIME_LIMIT_MS = 95;
    private boolean timeOut = false;
    private long nodeCount = 0;

    // --- PARAMÈTRES D'APPRENTISSAGE ---
    private int[] defaultWeights = {25, 28, -54, -36, 0, 0, 0, 0, 0, 0};
    private int[] weights = new int[10];

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
        System.arraycopy(defaultWeights, 0, weights, 0, 10);
    }

    @Override
    public void initialize() {
    }

    @Override
    public void finish() {
    }

    @Override
    public void learn() {
        long startTime = System.currentTimeMillis();

        System.err.println("Starting Benchmark on AwariLong...");
        int matches = 0;
        long benchStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - benchStart < 1000) {
            playMatch(defaultWeights, defaultWeights, 5);
            matches++;
        }
        System.err.println("Benchmark Result: " + matches + " matches/sec (depth 5)");

        int popSize = 20;
        int simDepth = 7;
        if (matches > 6000) { popSize = 40; simDepth = 9; }
        else if (matches > 3000) { popSize = 30; simDepth = 8; }

        long maxDuration = 3600000 - 60000;

        loadDataset("ia_project/data/awele.data");

        Integer[][] population = new Integer[popSize][11];

        for(int i=0; i<10; i++) population[0][i] = defaultWeights[i];
        population[0][10] = 0;

        for (int i = 1; i < popSize; i++) {
            population[i] = new Integer[11];
            for (int w = 0; w < 10; w++) {
                if (w < 4) population[i][w] = mutate(population[0][w]);
                else population[i][w] = random.nextInt(21) - 10;
            }
            population[i][10] = 0;
        }

        int generation = 0;
        while (System.currentTimeMillis() - startTime < maxDuration) {

            for (int i = 0; i < popSize; i++) {
                int[] w = toIntArray(population[i]);

                int scoreData = evaluateOnDataset(w);
                int scorePlay = 0;

                for (int j = 0; j < 3; j++) {
                    int opponentIdx = random.nextInt(popSize);
                    if (i == opponentIdx) continue;

                    int result = playMatch(w, toIntArray(population[opponentIdx]), 5);
                    if (result > 0) scorePlay += 50;
                    else if (result == 0) scorePlay += 10;
                }
                population[i][10] = scoreData + scorePlay;
            }

            Arrays.sort(population, Comparator.comparingInt(a -> -a[10]));

            if (generation % 5 == 0) {
                System.err.println("Gen " + generation + " | Best Fitness=" + population[0][10] + " | Weights=" + Arrays.toString(toIntArray(population[0])));
            }

            Integer[][] newPopulation = new Integer[popSize][11];
            for(int k=0; k<3; k++) newPopulation[k] = Arrays.copyOf(population[k], 11);

            for (int i = 3; i < popSize; i++) {
                Integer[] parent1 = population[random.nextInt(popSize / 2)];
                Integer[] parent2 = population[random.nextInt(popSize / 2)];
                Integer[] child = crossover(parent1, parent2);
                if (random.nextDouble() < 0.20) child = mutateGenome(child);
                newPopulation[i] = child;
            }
            population = newPopulation;
            generation++;
        }

        int[] bestLearned = toIntArray(population[0]);
        System.err.println("Learning finished. Best candidate: " + Arrays.toString(bestLearned));

        System.err.println("FINAL MATCH: Learned vs Default (Depth " + simDepth + ")...");
        int scoreLearned = 0;

        int res1 = playMatch(bestLearned, defaultWeights, simDepth);
        if (res1 > 0) scoreLearned += 3;
        else if (res1 == 0) scoreLearned += 1;

        int res2 = playMatch(defaultWeights, bestLearned, simDepth);
        if (res2 < 0) scoreLearned += 3;
        else if (res2 == 0) scoreLearned += 1;

        if (scoreLearned >= 4) {
            System.err.println("SUCCESS: Learned weights are BETTER! Updating bot.");
            System.arraycopy(bestLearned, 0, weights, 0, 10);
        } else {
            System.err.println("FAILURE: Default weights are still superior. Discarding learned weights.");
        }
    }

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
                for(int i=0; i<6; i++) data.myHoles[i] = Integer.parseInt(parts[i]);
                for(int i=0; i<6; i++) data.oppHoles[i] = Integer.parseInt(parts[6+i]);
                data.winning = parts[13].equals("G");
                dataset.add(data);
            }
        } catch (IOException e) {
            System.err.println("Warning: Dataset not found at " + path);
        }
    }

    // --- NOUVELLES FONCTIONS D'ÉVALUATION SÉPARÉES (ZÉRO ALLOCATION EN MATCH) ---

    // 1. Évaluation spécifique au Dataset (Utilisée uniquement pendant l'apprentissage)
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
        double score = 0; // Score simulé (0-0)
        int mySeeds = 0, oppSeeds = 0;
        int myMobility = 0, oppMobility = 0;

        for (int i = 0; i < 6; i++) {
            int s = myHoles[i];
            int os = oppHoles[i];
            mySeeds += s;
            oppSeeds += os;

            if (s > 0) myMobility++;
            if (os > 0) oppMobility++;

            if (s > 12) score += w[1];
            else if (s == 0) score += w[2];
            else if (s < 3) score += w[3];

            if (os > 12) score -= w[1];
            else if (os == 0) score -= w[2];
            else if (os < 3) score -= w[3];
        }
        score += w[4] * (mySeeds - oppSeeds);
        score += w[5] * (myMobility - oppMobility);
        return score;
    }

    // 2. Évaluation de Match (LECTURE DIRECTE DES BITS = ZÉRO OBJET CRÉÉ)
    private double evaluateSim(AwariLong board, int playerIndex, int[] w) {
        int oppIndex = 1 - playerIndex;
        int myScore = board.getScore(playerIndex);
        int oppScore = board.getScore(oppIndex);

        double score = w[0] * (myScore - oppScore);
        int mySeeds = 0, oppSeeds = 0;
        int myMobility = 0, oppMobility = 0;

        for (int i = 0; i < 6; i++) {
            // LECTURE DIRECTE DEPUIS LE LONG SANS CRÉER DE TABLEAU
            int s = board.getSeeds(playerIndex, i);
            int os = board.getSeeds(oppIndex, i);

            mySeeds += s;
            oppSeeds += os;

            if (s > 0) myMobility++;
            if (os > 0) oppMobility++;

            if (s > 12) score += w[1];
            else if (s == 0) score += w[2];
            else if (s < 3) score += w[3];

            if (os > 12) score -= w[1];
            else if (os == 0) score -= w[2];
            else if (os < 3) score -= w[3];
        }

        score += w[4] * (mySeeds - oppSeeds);
        score += w[5] * (myMobility - oppMobility);
        return score;
    }

    // --- HELPERS GÉNÉTIQUES ---
    private int[] toIntArray(Integer[] arr) {
        int[] res = new int[10];
        for(int i=0; i<10; i++) res[i] = arr[i];
        return res;
    }

    private Integer[] crossover(Integer[] p1, Integer[] p2) {
        Integer[] child = new Integer[11];
        int cut = random.nextInt(10);
        for (int i = 0; i < 10; i++) child[i] = (i < cut) ? p1[i] : p2[i];
        child[10] = 0;
        return child;
    }

    private Integer[] mutateGenome(Integer[] genome) {
        Integer[] newGenome = Arrays.copyOf(genome, 11);
        int geneIdx = random.nextInt(10);
        if (random.nextBoolean()) {
            double factor = 0.8 + (random.nextDouble() * 0.4);
            newGenome[geneIdx] = (int) (newGenome[geneIdx] * factor);
        } else {
            newGenome[geneIdx] += (random.nextInt(5) - 2);
        }
        return newGenome;
    }

    private int mutate(int value) {
        double factor = 0.8 + (random.nextDouble() * 0.4);
        return (int) (value * factor);
    }

    // --- MOTEUR DE SIMULATION AWARILONG ---
    private int playMatch(int[] w1, int[] w2, int depth) {
        AwariLong board = new AwariLong();
        int moves = 0;

        while ((48 - board.getScore(0) - board.getScore(1)) > 6 && moves < 150) {
            int cp = board.getCurrentPlayer();
            int[] cw = (cp == 0) ? w1 : w2;

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
        if (depth == 0 || (48 - board.getScore(0) - board.getScore(1)) < 6) return evaluateSim(board, myPlayerIndex, w);

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
            if (!hasMove) return evaluateSim(board, myPlayerIndex, w);
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
            if (!hasMove) return evaluateSim(board, myPlayerIndex, w);
            return minEval;
        }
    }

    // --- LOGIQUE DU BOT EN COMPÉTITION ---
    @Override
    public double[] getDecision(Board board) {
        int currentPlayer = board.getCurrentPlayer();
        int opponentPlayer = Board.otherPlayer(currentPlayer);

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
        this.nodeCount = 0;

        AwariLong fastRoot = AwariLong.fromBoard(board);

        for (int depth = 1; depth <= 60; depth++) {
            double[] currentDecisions = new double[Board.NB_HOLES];
            Arrays.fill(currentDecisions, Double.NEGATIVE_INFINITY);
            boolean searchCompleted = true;

            Integer[] moveOrder = {0, 1, 2, 3, 4, 5};
            if (depth > 1) {
                final double[] prev = bestDecisions;
                Arrays.sort(moveOrder, (a, b) -> Double.compare(prev[b], prev[a]));
            }

            for (int i : moveOrder) {
                if (fastRoot.isValidMove(currentPlayer, i)) {
                    AwariLong nextState = new AwariLong(fastRoot);
                    nextState.playMove(i);
                    double val = minimax(nextState, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, currentPlayer, startTime);

                    if (timeOut) { searchCompleted = false; break; }
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

    private double minimax(AwariLong board, int depth, double alpha, double beta, boolean maximizingPlayer, int myPlayerIndex, long startTime) {
        if ((nodeCount++ & 511) == 0) {
            if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
                timeOut = true;
                return 0.0;
            }
        }

        if (depth == 0 || (48 - board.getScore(0) - board.getScore(1)) < 6) {
            return evaluateSim(board, myPlayerIndex, weights); // LECTURE DIRECTE = VITESSE MAX
        }

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
            if (!hasMove) return evaluateSim(board, myPlayerIndex, weights);
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
            if (!hasMove) return evaluateSim(board, myPlayerIndex, weights);
            return minEval;
        }
    }
}