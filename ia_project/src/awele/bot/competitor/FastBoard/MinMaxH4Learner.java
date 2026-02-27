package awele.bot.competitor.FastBoard;

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

public class MinMaxH4Learner extends CompetitorBot {

    private static final long TIME_LIMIT_MS = 85;
    private boolean timeOut = false;
    
    // Poids par défaut (MinMaxFastBoard légèrement modifié)
    private int[] defaultWeights = {25, 28, -50, -36, 0, 0, 0, 0, 0, 0};
    private int[] weights = new int[10];
    
    private Random random;
    private List<TrainingData> dataset;
    
    private static class TrainingData {
        int[] myHoles;
        int[] oppHoles;
        boolean winning;
    }

    public MinMaxH4Learner() throws InvalidBotException {
        this.setBotName("MinMaxH4Learner");
        this.addAuthor("SABATIER Guillaume");
        this.random = new Random();
        this.dataset = new ArrayList<>();
        // Par défaut, on utilise les poids de base
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
        long maxDuration = 3300000; // 55 minutes

        loadDataset("C:/Users/Guillaume/projet-ia-Aw-l-/ia_project/data/awele.data");
        
        int popSize = 20;
        int simulationDepth = 6;
        
        Integer[][] population = new Integer[popSize][11];
        
        // Individu 0 : Poids par défaut
        for(int i=0; i<10; i++) population[0][i] = defaultWeights[i];
        population[0][10] = 0;
        
        // Initialisation
        for (int i = 1; i < popSize; i++) {
            population[i] = new Integer[11];
            for (int w = 0; w < 10; w++) {
                if (w < 4) population[i][w] = mutate(population[0][w]);
                else population[i][w] = random.nextInt(21) - 10;
            }
            population[i][10] = 0;
        }
        
        int generation = 0;
        while (System.currentTimeMillis() - startTime < maxDuration - 60000) { // On garde 1 min pour la finale
            for (int i = 0; i < popSize; i++) {
                int[] w = toIntArray(population[i]);
                int scoreData = evaluateOnDataset(w) * 20;
                int scorePlay = 0;
                for (int j = 0; j < 3; j++) {
                    int opponentIdx = random.nextInt(popSize);
                    if (i == opponentIdx) continue;
                    int result = playMatch(w, toIntArray(population[opponentIdx]), 4);
                    if (result > 0) scorePlay += 3;
                    else if (result == 0) scorePlay += 1;
                }
                population[i][10] = scoreData + scorePlay;
            }
            
            Arrays.sort(population, Comparator.comparingInt(a -> -a[10]));

            if (generation % 10 == 0) {
                System.err.println("Gen " + generation + " Best Score=" + population[0][10]);
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
        
        // FINALE : Meilleur Appris vs Poids par Défaut
        int[] bestLearned = toIntArray(population[0]);
        System.err.println("Learning finished. Best learned: " + Arrays.toString(bestLearned));
        
        System.err.println("Playing FINAL MATCH: Learned vs Default...");
        int scoreLearned = 0;
        
        // Match Aller
        int res1 = playMatch(bestLearned, defaultWeights, 6);
        if (res1 > 0) scoreLearned += 3;
        else if (res1 == 0) scoreLearned += 1;
        
        // Match Retour
        int res2 = playMatch(defaultWeights, bestLearned, 6); // res2 = Default - Learned
        if (res2 < 0) scoreLearned += 3; // Learned gagne
        else if (res2 == 0) scoreLearned += 1;
        
        if (scoreLearned >= 4) { // Il faut gagner au moins une fois et faire nul, ou gagner 2 fois
            System.err.println("Learned weights are BETTER! Switching.");
            weights = bestLearned;
        } else {
            System.err.println("Default weights are still better (or equal). Keeping default.");
            weights = defaultWeights;
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
        } catch (IOException e) {}
    }
    
    private int evaluateOnDataset(int[] w) {
        int score = 0;
        for (TrainingData data : dataset) {
            double eval = evaluateSimRaw(data.myHoles, data.oppHoles, 0, 0, w);
            if (data.winning && eval > 0) score += 1;
            else if (!data.winning && eval < 0) score += 1;
        }
        return score;
    }
    
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
    
    private int playMatch(int[] w1, int[] w2, int depth) {
        FastBoard board = new FastBoard();
        int moves = 0;
        while (board.getTotalSeeds() > 6 && moves < 200) {
            int cp = board.currentPlayer;
            int[] cw = (cp == 0) ? w1 : w2;
            int bestMove = -1;
            double bestVal = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    FastBoard next = board.playMove(i);
                    double val = minimaxSim(next, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, cp, cw);
                    if (val > bestVal) { bestVal = val; bestMove = i; }
                }
            }
            if (bestMove == -1) break;
            board = board.playMove(bestMove);
            moves++;
        }
        int s1 = board.scores[0];
        int s2 = board.scores[1];
        int seeds = board.getTotalSeeds();
        if (board.currentPlayer == 0) s2 += seeds; else s1 += seeds;
        return Integer.compare(s1, s2);
    }
    
    private double minimaxSim(FastBoard board, int depth, double alpha, double beta, boolean maximizingPlayer, int myPlayerIndex, int[] w) {
        if (depth == 0 || board.getTotalSeeds() < 2) return evaluateSim(board, myPlayerIndex, w);
        boolean hasMove = false;
        for(int i=0; i<6; i++) if (board.isValid(i)) { hasMove = true; break; }
        if (!hasMove) {
             int seeds = board.getTotalSeeds();
             int sP = board.scores[myPlayerIndex];
             int sO = board.scores[1-myPlayerIndex];
             if (board.currentPlayer == myPlayerIndex) sO += seeds; else sP += seeds;
             return 10000.0 * (sP - sO);
        }
        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    FastBoard next = board.playMove(i);
                    double eval = minimaxSim(next, depth - 1, alpha, beta, false, myPlayerIndex, w);
                    maxEval = Math.max(maxEval, eval);
                    alpha = Math.max(alpha, eval);
                    if (beta <= alpha) break;
                }
            }
            return maxEval;
        } else {
            double minEval = Double.POSITIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    FastBoard next = board.playMove(i);
                    double eval = minimaxSim(next, depth - 1, alpha, beta, true, myPlayerIndex, w);
                    minEval = Math.min(minEval, eval);
                    beta = Math.min(beta, eval);
                    if (beta <= alpha) break;
                }
            }
            return minEval;
        }
    }
    
    private double evaluateSimRaw(int[] myHoles, int[] oppHoles, int myScore, int oppScore, int[] w) {
        double score = w[0] * (myScore - oppScore);
        int mySeeds = 0, oppSeeds = 0;
        int myMobility = 0, oppMobility = 0;
        for (int i = 0; i < 6; i++) {
            int s = myHoles[i];
            int os = oppHoles[i];
            mySeeds += s; oppSeeds += os;
            if (s > 0) myMobility++;
            if (os > 0) oppMobility++;
            if (s > 12) score += w[1]; else if (s == 0) score += w[2]; else if (s < 3) score += w[3];
            if (os > 12) score -= w[1]; else if (os == 0) score -= w[2]; else if (os < 3) score -= w[3];
        }
        score += w[4] * (mySeeds - oppSeeds);
        score += w[5] * (myMobility - oppMobility);
        if (oppSeeds < 10) score += w[6];
        score += w[7];
        score += w[8] * mySeeds;
        score += w[9] * oppSeeds;
        return score;
    }
    
    private double evaluateSim(FastBoard board, int playerIndex, int[] w) {
        int opponentIndex = 1 - playerIndex;
        int[] myHoles = new int[6];
        int[] oppHoles = new int[6];
        for(int i=0; i<6; i++) {
            myHoles[i] = board.holes[playerIndex * 6 + i];
            oppHoles[i] = board.holes[opponentIndex * 6 + i];
        }
        return evaluateSimRaw(myHoles, oppHoles, board.scores[playerIndex], board.scores[opponentIndex], w);
    }

    @Override
    public double[] getDecision(Board board) {
        if (board.getLog(board.getCurrentPlayer()).length == 0 && board.getLog(Board.otherPlayer(board.getCurrentPlayer())).length == 0) {
            double[] firstMoveDecision = new double[Board.NB_HOLES];
            Arrays.fill(firstMoveDecision, Double.NEGATIVE_INFINITY);
            if (board.validMoves(board.getCurrentPlayer())[5]) {
                firstMoveDecision[5] = 1.0;
                return firstMoveDecision;
            }
        }

        double[] bestDecisions = new double[Board.NB_HOLES];
        Arrays.fill(bestDecisions, Double.NEGATIVE_INFINITY);
        long startTime = System.currentTimeMillis();
        timeOut = false;
        FastBoard rootState = new FastBoard(board);
        int currentPlayer = board.getCurrentPlayer();
        for (int depth = 1; depth <= 60; depth++) {
            double[] currentDecisions = new double[Board.NB_HOLES];
            boolean searchCompleted = true;
            
            Integer[] rootMoves = {0, 1, 2, 3, 4, 5};
            if (depth > 1) {
                final double[] prevDecisions = bestDecisions;
                Arrays.sort(rootMoves, (a, b) -> Double.compare(prevDecisions[b], prevDecisions[a]));
            }
            
            for (int i : rootMoves) {
                if (rootState.isValid(i)) {
                    FastBoard nextState = rootState.playMove(i);
                    double val = minimax(nextState, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, currentPlayer, startTime);
                    if (timeOut) { searchCompleted = false; break; }
                    currentDecisions[i] = val;
                } else {
                    currentDecisions[i] = Double.NEGATIVE_INFINITY;
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
        if ((nodes++ & 511) == 0) {
            if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) { timeOut = true; return 0; }
        }
        if (depth == 0 || board.getTotalSeeds() < 2) return evaluateSim(board, myPlayerIndex, weights);
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
                }
            }
            if (!hasMove) return evaluateSim(board, myPlayerIndex, weights);
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
            if (!hasMove) return evaluateSim(board, myPlayerIndex, weights);
            return minEval;
        }
    }
    
    private long nodes = 0;

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